package iwarok.app.data

import iwarok.app.core.AuthState
import iwarok.app.core.IwaraException
import iwarok.app.core.IwaraUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/* ------------------------------------------------------------------------- *
 * Token helpers.
 * ------------------------------------------------------------------------- */

internal object TokenExpiry {

    /** Used when upstream omits expiry information and the JWT has no `exp`. */
    const val DEFAULT_ACCESS_TTL_MS: Long = 10 * 60 * 1000L

    /** Reads the `exp` claim (seconds) from a JWT without verifying the signature. */
    fun jwtExpiryEpochMillis(token: String): Long? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return try {
            val payload = base64UrlDecode(parts[1]) ?: return null
            val element = iwaraJson.parseToJsonElement(String(payload, Charsets.UTF_8))
            val exp = (element as? JsonObject)?.get("exp")?.jsonPrimitive?.longOrNull ?: return null
            if (exp <= 0) null else exp * 1000L
        } catch (_: Exception) {
            null
        }
    }

    fun resolve(
        expiresInSeconds: Long?,
        expiresAt: Long?,
        accessToken: String,
        now: Long,
    ): Long {
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            return now + expiresInSeconds * 1000L
        }
        if (expiresAt != null && expiresAt > 0) {
            // Be tolerant about seconds vs milliseconds.
            return if (expiresAt < 100_000_000_000L) expiresAt * 1000L else expiresAt
        }
        return jwtExpiryEpochMillis(accessToken) ?: (now + DEFAULT_ACCESS_TTL_MS)
    }

    private fun base64UrlDecode(input: String): ByteArray? {
        val padded = when (input.length % 4) {
            2 -> "$input=="
            3 -> "$input="
            else -> input
        }
        return try {
            Base64.getUrlDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/* ------------------------------------------------------------------------- *
 * Auth wire operations, isolated behind an interface so [AuthManager] can be
 * unit-tested with an in-memory fake.
 * ------------------------------------------------------------------------- */

/**
 * Statuses that mean "this credential can no longer be used".
 *
 * Real-world note (verified against the live API): a fake or expired refresh
 * token makes `POST /user/token` answer **403 `errors.forbidden`**, not 401, and
 * malformed tokens answer 400. A 403 therefore must drop the session instead of
 * leaving the user stuck as a guest with a dead token on disk.
 */
internal fun isInvalidCredentialStatus(statusCode: Int): Boolean =
    statusCode == 400 || statusCode == 401 || statusCode == 403

/** Normalises refresh-token exchange failures into a clear user-facing error. */
internal fun mapRefreshExchangeFailure(error: IwaraException): IwaraException =
    if (isInvalidCredentialStatus(error.statusCode)) {
        IwaraException("登录凭证无效或已过期，请重新登录", 401, error)
    } else {
        error
    }

internal data class TokenResult(
    val accessToken: String,
    val expiresAtEpochMillis: Long,
)

internal interface AuthApi {
    /** Returns the long-lived refresh token. */
    suspend fun login(email: String, password: String): String

    /** Exchanges a refresh token for a short-lived access token. */
    suspend fun exchangeToken(refreshToken: String): TokenResult

    /** GET /user with the given access token. */
    suspend fun fetchCurrentUser(accessToken: String): IwaraUser
}

internal class IwaraAuthApi(
    private val http: IwaraHttp,
    private val clock: () -> Long = System::currentTimeMillis,
) : AuthApi {

    override suspend fun login(email: String, password: String): String {
        val payload = iwaraJson.encodeToString(LoginRequest.serializer(), LoginRequest(email.trim(), password))
        val raw = http.requestJson("POST", "/user/login", jsonBody = payload)
        val response = decode(LoginResponse.serializer(), raw, "登录失败：返回数据异常")
        if (response.token.isBlank()) throw IwaraException("登录失败：未获取到登录凭证")
        return response.token
    }

    override suspend fun exchangeToken(refreshToken: String): TokenResult {
        if (refreshToken.isBlank()) {
            throw IwaraException("登录凭证无效或已过期，请重新登录", 401)
        }
        val raw = try {
            http.requestJson("POST", "/user/token", bearerToken = refreshToken)
        } catch (e: IwaraException) {
            throw mapRefreshExchangeFailure(e)
        }
        val response = decode(TokenResponse.serializer(), raw, "登录凭证无效或已过期，请重新登录")
        if (response.accessToken.isBlank()) {
            throw IwaraException("登录凭证无效或已过期，请重新登录", 401)
        }
        val expiry = TokenExpiry.resolve(
            expiresInSeconds = response.expiresIn,
            expiresAt = response.expiresAt,
            accessToken = response.accessToken,
            now = clock(),
        )
        return TokenResult(response.accessToken, expiry)
    }

    override suspend fun fetchCurrentUser(accessToken: String): IwaraUser {
        val raw = http.requestJson("GET", "/user", bearerToken = accessToken)
        // Normally {"user": {...}}; tolerate a bare user object as well.
        val user = try {
            decode(UserEnvelope.serializer(), raw, "获取用户信息失败").user
        } catch (_: IwaraException) {
            decode(IwaraUser.serializer(), raw, "获取用户信息失败")
        }
        if (user.id.isBlank() && user.username.isBlank()) {
            throw IwaraException("获取用户信息失败，请重新登录", 401)
        }
        return user
    }

    private fun <T> decode(serializer: KSerializer<T>, raw: String, errorMessage: String): T =
        try {
            iwaraJson.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            throw IwaraException(errorMessage, 0, e)
        }
}

/* ------------------------------------------------------------------------- *
 * Session lifecycle.
 *
 * Owns the [AuthState] flow, login/logout, token renewal (120 s ahead of expiry
 * and forced refresh after a 401) and persistence through [SessionStore].
 * ------------------------------------------------------------------------- */

internal class AuthManager(
    private val api: AuthApi,
    private val store: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val mutex = Mutex()
    private var session: PersistedSession? = null

    fun isLoggedIn(): Boolean = _authState.value is AuthState.LoggedIn

    /** Restores a persisted session. Always terminates in Guest or LoggedIn. */
    suspend fun init() {
        mutex.withLock {
            val persisted = try {
                store.load()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }
            session = persisted
            if (persisted == null || !persisted.hasAnyToken) {
                _authState.value = AuthState.Guest
                return
            }
            restoreLocked(persisted)
        }
    }

    suspend fun login(email: String, password: String): Result<IwaraUser> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            return Result.failure(IwaraException("请输入邮箱和密码"))
        }
        return try {
            val refreshToken = api.login(trimmedEmail, password)
            val token = api.exchangeToken(refreshToken)
            val user = api.fetchCurrentUser(token.accessToken)
            persist(
                PersistedSession(
                    refreshToken = refreshToken,
                    accessToken = token.accessToken,
                    accessExpiryEpochMillis = token.expiresAtEpochMillis,
                    user = user,
                )
            )
            Result.success(user)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IwaraException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IwaraException("登录失败，请检查网络后重试", 0, e))
        }
    }

    /** Accepts either a refresh token or an access token. */
    suspend fun loginWithToken(rawToken: String): Result<IwaraUser> {
        val token = rawToken.trim().removePrefix("Bearer ").trim()
        if (token.isEmpty()) {
            return Result.failure(IwaraException("请输入有效的 Token"))
        }
        // 1) Try the refresh-token flow first: it yields a renewable session.
        val exchanged = exchangeRefreshToken(token)
        val refreshResult = exchanged.getOrNull()
        if (refreshResult != null) {
            val user = fetchUserOrNull(refreshResult.accessToken)
            if (user != null) {
                persist(
                    PersistedSession(
                        refreshToken = token,
                        accessToken = refreshResult.accessToken,
                        accessExpiryEpochMillis = refreshResult.expiresAtEpochMillis,
                        user = user,
                    )
                )
                return Result.success(user)
            }
        }
        // 2) Fall back to treating the pasted value as a short-lived access token.
        val user = fetchUserOrNull(token)
        if (user != null) {
            val expiry = TokenExpiry.jwtExpiryEpochMillis(token)
                ?: (clock() + TokenExpiry.DEFAULT_ACCESS_TTL_MS)
            persist(
                PersistedSession(
                    refreshToken = "",
                    accessToken = token,
                    accessExpiryEpochMillis = expiry,
                    user = user,
                )
            )
            return Result.success(user)
        }
        return Result.failure(IwaraException("Token 无效或已过期，请检查后重试", 401))
    }

    suspend fun logout() {
        mutex.withLock {
            session = null
            store.clear()
            _authState.value = AuthState.Guest
        }
    }

    /**
     * Best-effort access token for guest-capable endpoints. Returns null for a
     * guest; transparently renews an expired access token when possible.
     */
    suspend fun optionalAccessToken(): String? = mutex.withLock {
        val current = session ?: return@withLock null
        if (current.accessToken.isNotBlank() && !isExpiring(current.accessExpiryEpochMillis)) {
            return@withLock current.accessToken
        }
        if (current.refreshToken.isBlank()) {
            // Unknown/expired access token, no way to renew: still try it, a 401
            // makes callers retry as a guest.
            return@withLock current.accessToken.takeIf { it.isNotBlank() }
        }
        refreshLocked(current)?.accessToken
    }

    /**
     * Renews the access token when it expires within 120 s or when [force] is
     * true (used after a 401). Returns null when no valid token can be obtained.
     */
    suspend fun refreshAccessToken(force: Boolean = false): String? = mutex.withLock {
        val current = session ?: return@withLock null
        if (!force && current.accessToken.isNotBlank() && !isExpiring(current.accessExpiryEpochMillis)) {
            return@withLock current.accessToken
        }
        if (current.refreshToken.isBlank()) {
            if (force) {
                // A 401 proved the token dead; there is nothing to renew it with.
                session = null
                store.clear()
                _authState.value = AuthState.Guest
            }
            return@withLock null
        }
        refreshLocked(current)?.accessToken
    }

    /* ----------------------------- internals ----------------------------- */

    private fun isExpiring(expiryEpochMillis: Long): Boolean {
        if (expiryEpochMillis <= 0L) return false
        return expiryEpochMillis - clock() <= EXPIRY_MARGIN_MS
    }

    private suspend fun restoreLocked(persisted: PersistedSession) {
        // 1) The persisted access token may still be valid.
        if (persisted.accessToken.isNotBlank() && !isExpiring(persisted.accessExpiryEpochMillis)) {
            try {
                val user = api.fetchCurrentUser(persisted.accessToken)
                persistLocked(persisted.copy(user = user))
                return
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                val cachedUser = persisted.user
                if (cachedUser != null && !e.isAuthFailure()) {
                    // Offline start: show the cached account instead of logging out.
                    persistLocked(persisted)
                    return
                }
            }
        }
        // 2) Exchange the refresh token.
        var authFailure = false
        if (persisted.refreshToken.isNotBlank()) {
            val exchanged = exchangeRefreshToken(persisted.refreshToken)
            val token = exchanged.getOrNull()
            if (token != null) {
                val updated = persisted.copy(
                    accessToken = token.accessToken,
                    accessExpiryEpochMillis = token.expiresAtEpochMillis,
                )
                val user = fetchUserOrNull(token.accessToken) ?: persisted.user
                if (user != null) {
                    persistLocked(updated.copy(user = user))
                    return
                }
                // Token is valid but the profile request failed; keep the session
                // for later requests and treat startup as a guest.
                session = updated
                runCatching { store.save(updated) }
            } else {
                authFailure = exchanged.exceptionOrNull().isAuthFailure()
            }
        } else {
            authFailure = true
        }
        // 3) Nothing worked: guest. Clear only when the credential itself is dead.
        if (authFailure) store.clear()
        session = null
        _authState.value = AuthState.Guest
    }

    private suspend fun refreshLocked(current: PersistedSession): TokenResult? {
        val exchanged = exchangeRefreshToken(current.refreshToken)
        val token = exchanged.getOrNull()
        if (token == null) {
            if (exchanged.exceptionOrNull().isAuthFailure()) {
                session = null
                store.clear()
                _authState.value = AuthState.Guest
            }
            return null
        }
        val user = current.user ?: fetchUserOrNull(token.accessToken)
        val updated = current.copy(
            accessToken = token.accessToken,
            accessExpiryEpochMillis = token.expiresAtEpochMillis,
            user = user ?: current.user,
        )
        session = updated
        runCatching { store.save(updated) }
        if (user != null && _authState.value !is AuthState.LoggedIn) {
            _authState.value = AuthState.LoggedIn(user)
        }
        return token
    }

    private suspend fun exchangeRefreshToken(refreshToken: String): Result<TokenResult> {
        if (refreshToken.isBlank()) {
            return Result.failure(IwaraException("登录凭证无效或已过期，请重新登录", 401))
        }
        return try {
            Result.success(api.exchangeToken(refreshToken))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchUserOrNull(accessToken: String): IwaraUser? = try {
        api.fetchCurrentUser(accessToken)
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Exception) {
        null
    }

    /** Atomically updates the session from a public (unlocked) entry point. */
    private suspend fun persist(newSession: PersistedSession) {
        mutex.withLock { persistLocked(newSession) }
    }

    /** Requires the mutex to be already held by the caller. */
    private suspend fun persistLocked(newSession: PersistedSession) {
        session = newSession
        runCatching { store.save(newSession) }
        val user = newSession.user ?: IwaraUser()
        _authState.value = AuthState.LoggedIn(user)
    }

    private fun Throwable?.isAuthFailure(): Boolean =
        this is IwaraException && isInvalidCredentialStatus(statusCode)

    private companion object {
        const val EXPIRY_MARGIN_MS = 120_000L
    }
}
