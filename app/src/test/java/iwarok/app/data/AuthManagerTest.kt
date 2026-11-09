package iwarok.app.data

import iwarok.app.core.AuthState
import iwarok.app.core.IwaraException
import iwarok.app.core.IwaraUser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Base64

/**
 * Session lifecycle tests. Network and storage are faked so this runs as a pure
 * JVM test: Android's EncryptedSharedPreferences path is intentionally not
 * exercised here (it is injected behind [SessionStore]).
 */
class AuthManagerTest {

    private var now: Long = 1_000_000L
    private val api = FakeAuthApi { now }
    private val store = InMemorySessionStore()

    private fun manager(): AuthManager = AuthManager(api, store, clock = { now })

    /* ------------------------------- helpers ------------------------------- */

    private class InMemorySessionStore : SessionStore {
        var saved: PersistedSession? = null
        var clearCount = 0

        override fun load(): PersistedSession? = saved

        override fun save(session: PersistedSession) {
            saved = session
        }

        override fun clear() {
            saved = null
            clearCount++
        }
    }

    private class FakeAuthApi(private val clock: () -> Long) : AuthApi {
        var loginToken: String = "refresh-token"
        var loginEmail: String? = null
        var loginError: Exception? = null

        var exchangeError: Exception? = null
        var exchangeCalls: MutableList<String> = mutableListOf()
        var accessCounter: Int = 0
        var tokenTtlMs: Long = 600_000L

        var currentUser: IwaraUser = IwaraUser(id = "user1", username = "tester", name = "Tester")
        var userError: Exception? = null
        var userCalls: MutableList<String> = mutableListOf()

        override suspend fun login(email: String, password: String): String {
            loginError?.let { throw it }
            loginEmail = email
            return loginToken
        }

        override suspend fun exchangeToken(refreshToken: String): TokenResult {
            exchangeCalls += refreshToken
            exchangeError?.let { throw it }
            accessCounter += 1
            return TokenResult("access-$accessCounter", clock() + tokenTtlMs)
        }

        override suspend fun fetchCurrentUser(accessToken: String): IwaraUser {
            userCalls += accessToken
            userError?.let { throw it }
            return currentUser
        }
    }

    /* -------------------------------- init -------------------------------- */

    @Test
    fun `init without a stored session becomes guest`() = runTest {
        val manager = manager()
        manager.init()
        assertTrue(manager.authState.value is AuthState.Guest)
        assertTrue(api.exchangeCalls.isEmpty())
    }

    @Test
    fun `init restores a still valid access token without exchanging`() = runTest {
        store.saved = PersistedSession(
            refreshToken = "refresh-1",
            accessToken = "access-1",
            accessExpiryEpochMillis = now + 600_000L,
            user = IwaraUser(id = "cached", username = "cached"),
        )
        val manager = manager()
        manager.init()
        assertTrue(manager.authState.value is AuthState.LoggedIn)
        assertTrue(api.exchangeCalls.isEmpty())
        assertEquals(listOf("access-1"), api.userCalls)
    }

    @Test
    fun `init renews an access token that expires within the margin`() = runTest {
        store.saved = PersistedSession(
            refreshToken = "refresh-1",
            accessToken = "access-old",
            accessExpiryEpochMillis = now + 60_000L,
            user = null,
        )
        val manager = manager()
        manager.init()
        assertTrue(manager.authState.value is AuthState.LoggedIn)
        assertEquals(listOf("refresh-1"), api.exchangeCalls)
        assertEquals("access-1", store.saved?.accessToken)
    }

    @Test
    fun `init keeps the cached profile when offline`() = runTest {
        store.saved = PersistedSession(
            refreshToken = "refresh-1",
            accessToken = "access-1",
            accessExpiryEpochMillis = now + 600_000L,
            user = IwaraUser(id = "cached", username = "cached"),
        )
        api.userError = IOException("offline")
        val manager = manager()
        manager.init()
        assertTrue(manager.authState.value is AuthState.LoggedIn)
        assertEquals("cached", (manager.authState.value as AuthState.LoggedIn).user.id)
    }

    @Test
    fun `init becomes guest and clears storage when the refresh token is rejected`() = runTest {
        store.saved = PersistedSession(
            refreshToken = "refresh-dead",
            accessToken = "access-old",
            accessExpiryEpochMillis = now + 60_000L,
            user = null,
        )
        api.exchangeError = IwaraException("登录凭证无效或已过期，请重新登录", 401)
        val manager = manager()
        manager.init()
        assertTrue(manager.authState.value is AuthState.Guest)
        assertNull(store.saved)
        assertTrue(store.clearCount >= 1)
    }

    /* -------------------------------- login -------------------------------- */

    @Test
    fun `login stores tokens and publishes logged in`() = runTest {
        val manager = manager()
        val result = manager.login(" tester@example.com ", "secret-password")
        assertTrue(result.isSuccess)
        assertEquals("tester@example.com", api.loginEmail)
        assertEquals("user1", result.getOrThrow().id)

        val saved = store.saved
        assertEquals("refresh-token", saved?.refreshToken)
        assertEquals("access-1", saved?.accessToken)
        assertEquals(now + 600_000L, saved?.accessExpiryEpochMillis)
        assertEquals("user1", saved?.user?.id)
        // Never persist the cleartext password.
        assertTrue(saved.toString().contains("secret-password").not())
        assertTrue(manager.authState.value is AuthState.LoggedIn)
    }

    @Test
    fun `login rejects blank credentials without a network call`() = runTest {
        val manager = manager()
        val result = manager.login("   ", "")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.isNotBlank() == true)
        assertTrue(api.exchangeCalls.isEmpty())
    }

    @Test
    fun `login surfaces the upstream failure message`() = runTest {
        api.loginError = IwaraException("邮箱或密码错误，请检查后重试", 400)
        val manager = manager()
        val result = manager.login("a@b.c", "wrong")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("邮箱") == true)
    }

    @Test
    fun `loginWithToken treats the pasted value as a refresh token`() = runTest {
        val manager = manager()
        val result = manager.loginWithToken("pasted-refresh")
        assertTrue(result.isSuccess)
        assertEquals(listOf("pasted-refresh"), api.exchangeCalls)
        assertEquals("pasted-refresh", store.saved?.refreshToken)
        assertEquals("access-1", store.saved?.accessToken)
        assertTrue(manager.authState.value is AuthState.LoggedIn)
    }

    @Test
    fun `loginWithToken falls back to treating the value as an access token`() = runTest {
        api.exchangeError = IwaraException("登录凭证无效或已过期，请重新登录", 401)
        val manager = manager()
        val result = manager.loginWithToken("Bearer pasted-access")
        assertTrue(result.isSuccess)
        assertEquals("pasted-access", store.saved?.accessToken)
        assertEquals("", store.saved?.refreshToken)
        assertTrue(api.userCalls.contains("pasted-access"))
    }

    @Test
    fun `loginWithToken rejects an unusable token`() = runTest {
        api.exchangeError = IwaraException("登录凭证无效或已过期，请重新登录", 401)
        api.userError = IwaraException("登录已失效，请重新登录", 401)
        val manager = manager()
        val result = manager.loginWithToken("garbage")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Token") == true)
    }

    /* ------------------------------ refreshing ------------------------------ */

    @Test
    fun `optionalAccessToken reuses a fresh token`() = runTest {
        store.saved = PersistedSession("refresh-1", "access-1", now + 600_000L, null)
        val manager = manager()
        manager.init()
        assertEquals("access-1", manager.optionalAccessToken())
        assertTrue(api.exchangeCalls.isEmpty())
    }

    @Test
    fun `optionalAccessToken renews once the 120 second margin is reached`() = runTest {
        store.saved = PersistedSession("refresh-1", "access-old", now + 600_000L, null)
        val manager = manager()
        manager.init()
        // Move the clock so only 50 s of validity remain (< 120 s margin).
        now += 550_000L
        val token = manager.optionalAccessToken()
        assertEquals("access-1", token)
        assertEquals(listOf("refresh-1"), api.exchangeCalls)
        assertTrue(manager.authState.value is AuthState.LoggedIn)
    }

    @Test
    fun `refreshAccessToken force renews even when the token is still valid`() = runTest {
        store.saved = PersistedSession("refresh-1", "access-old", now + 600_000L, null)
        val manager = manager()
        manager.init()
        assertEquals("access-1", manager.refreshAccessToken(force = true))
        assertEquals(1, api.exchangeCalls.size)
    }

    @Test
    fun `refreshAccessToken drops the session when the refresh token is rejected`() = runTest {
        store.saved = PersistedSession("refresh-dead", "access-1", now + 600_000L, null)
        val manager = manager()
        manager.init()
        api.exchangeError = IwaraException("登录凭证无效或已过期，请重新登录", 401)
        assertNull(manager.refreshAccessToken(force = true))
        assertTrue(manager.authState.value is AuthState.Guest)
        assertNull(store.saved)
    }

    @Test
    fun `refreshAccessToken drops the session when the refresh token is rejected with 403`() = runTest {
        // Live API fact: POST /user/token with a dead refresh token answers
        // 403 errors.forbidden (not 401). The session must still be cleared.
        store.saved = PersistedSession("refresh-dead", "access-1", now + 600_000L, null)
        val manager = manager()
        manager.init()
        api.exchangeError = IwaraException("访问被拒绝（403），可能触发了风控", 403)
        assertNull(manager.refreshAccessToken(force = true))
        assertTrue(manager.authState.value is AuthState.Guest)
        assertNull(store.saved)
    }

    @Test
    fun `init becomes guest and clears storage when refresh exchange returns 403`() = runTest {
        store.saved = PersistedSession(
            refreshToken = "refresh-dead",
            accessToken = "access-old",
            accessExpiryEpochMillis = now + 60_000L,
            user = null,
        )
        api.exchangeError = IwaraException("访问被拒绝（403），可能触发了风控", 403)
        val manager = manager()
        manager.init()
        assertTrue(manager.authState.value is AuthState.Guest)
        assertNull(store.saved)
    }

    @Test
    fun `invalid credential statuses are 400 401 and 403`() {
        assertTrue(isInvalidCredentialStatus(400))
        assertTrue(isInvalidCredentialStatus(401))
        assertTrue(isInvalidCredentialStatus(403))
        assertFalse(isInvalidCredentialStatus(0))
        assertFalse(isInvalidCredentialStatus(429))
        assertFalse(isInvalidCredentialStatus(500))
    }

    @Test
    fun `refresh exchange failures are mapped to a clear login error`() {
        val forbidden = mapRefreshExchangeFailure(IwaraException("访问被拒绝（403），可能触发了风控", 403))
        assertEquals(401, forbidden.statusCode)
        assertTrue(forbidden.message.orEmpty().contains("登录凭证无效"))

        val network = mapRefreshExchangeFailure(IwaraException("网络连接失败，请检查网络或代理设置", 0))
        assertEquals(0, network.statusCode)
        assertTrue(network.message.orEmpty().contains("网络"))
    }

    /* -------------------------------- logout -------------------------------- */

    @Test
    fun `logout clears the session and storage`() = runTest {
        store.saved = PersistedSession("refresh-1", "access-1", now + 600_000L, null)
        val manager = manager()
        manager.init()
        assertTrue(manager.authState.value is AuthState.LoggedIn)
        manager.logout()
        assertTrue(manager.authState.value is AuthState.Guest)
        assertNull(store.saved)
        assertNull(manager.optionalAccessToken())
    }

    /* ------------------------------ token expiry ------------------------------ */

    @Test
    fun `jwt expiry is read from the exp claim`() {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":1762500000,"sub":"user1"}""".toByteArray(Charsets.UTF_8))
        val token = "eyJhbGciOiJIUzI1NiJ9.$payload.signature"
        assertEquals(1762500000000L, TokenExpiry.jwtExpiryEpochMillis(token))
        assertNull(TokenExpiry.jwtExpiryEpochMillis("opaque-token"))
        assertNull(TokenExpiry.jwtExpiryEpochMillis(""))
    }

    @Test
    fun `token expiry resolution prefers explicit ttl then jwt then default`() {
        assertEquals(1_300_000L, TokenExpiry.resolve(300L, null, "opaque", 1_000_000L))
        // expiresAt in seconds and milliseconds are both understood.
        assertEquals(
            1_762_500_000_000L,
            TokenExpiry.resolve(null, 1_762_500_000L, "opaque", 1_000_000L),
        )
        assertEquals(
            1_762_500_000_000L,
            TokenExpiry.resolve(null, 1_762_500_000_000L, "opaque", 1_000_000L),
        )
        assertEquals(
            1_000_000L + TokenExpiry.DEFAULT_ACCESS_TTL_MS,
            TokenExpiry.resolve(null, null, "opaque", 1_000_000L),
        )
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":1762500000}""".toByteArray(Charsets.UTF_8))
        val jwt = "header.$payload.sig"
        assertEquals(
            1762500000000L,
            TokenExpiry.resolve(null, null, jwt, 1_000_000L),
        )
    }
}
