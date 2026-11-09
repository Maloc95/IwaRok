package iwarok.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraUser

/**
 * Persisted login session. Tokens are opaque strings; [user] is only a cache so
 * the UI can render the profile while offline (it is refreshed from /user).
 */
internal data class PersistedSession(
    val refreshToken: String = "",
    val accessToken: String = "",
    val accessExpiryEpochMillis: Long = 0L,
    val user: IwaraUser? = null,
) {
    val hasAnyToken: Boolean get() = refreshToken.isNotBlank() || accessToken.isNotBlank()
}

/**
 * Storage abstraction so [AuthManager] stays unit-testable on the JVM without
 * any Android dependency. Production uses [AndroidSessionStore].
 */
internal interface SessionStore {
    fun load(): PersistedSession?
    fun save(session: PersistedSession)
    fun clear()
}

/**
 * Persists the session with [EncryptedSharedPreferences] and transparently falls
 * back to plain [SharedPreferences] when the Android Keystore is unavailable
 * (broken/rotated keys, unsupported devices, corrupted master key, ...).
 *
 * Plain-text passwords are never stored; only refresh/access tokens and a cached
 * user JSON blob are kept.
 */
internal class AndroidSessionStore(context: Context) : SessionStore {

    private val appContext: Context = context.applicationContext

    private val plainPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(IwaraConfig.PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Keystore access can be slow; create the encrypted store lazily on first
    // use (from a coroutine on Dispatchers.IO) instead of during app startup.
    private val securePrefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                IwaraConfig.SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun load(): PersistedSession? {
        val values = readAll()
        val refreshToken = values[KEY_REFRESH_TOKEN].orEmpty()
        val accessToken = values[KEY_ACCESS_TOKEN].orEmpty()
        val expiry = values[KEY_ACCESS_EXPIRY]?.toLongOrNull() ?: 0L
        val user = values[KEY_USER]?.let { raw ->
            runCatching { iwaraJson.decodeFromString<IwaraUser>(raw) }.getOrNull()
        }
        val session = PersistedSession(refreshToken, accessToken, expiry, user)
        return session.takeIf { it.hasAnyToken }
    }

    override fun save(session: PersistedSession) {
        val values = mapOf(
            KEY_REFRESH_TOKEN to session.refreshToken,
            KEY_ACCESS_TOKEN to session.accessToken,
            KEY_ACCESS_EXPIRY to session.accessExpiryEpochMillis.toString(),
            KEY_USER to (session.user?.let { runCatching { iwaraJson.encodeToString(IwaraUser.serializer(), it) }.getOrNull() } ?: ""),
        )
        val secure = securePrefs
        val secureWritten = secure != null && runCatching {
            val editor = secure.edit()
            values.forEach { (key, value) -> editor.putString(key, value) }
            editor.commit()
        }.getOrDefault(false)
        if (!secureWritten) {
            // Degraded mode: keystore can be unavailable on some devices/backups.
            runCatching {
                val editor = plainPrefs.edit()
                values.forEach { (key, value) -> editor.putString(key, value) }
                editor.commit()
            }
        }
    }

    override fun clear() {
        runCatching { securePrefs?.edit()?.clear()?.commit() }
        runCatching { plainPrefs.edit().clear().commit() }
    }

    private fun readAll(): Map<String, String> {
        val secure = safeRead(securePrefs)
        val plain = safeRead(plainPrefs)
        // Values written before a keystore failure live in the plain prefs; the
        // encrypted store wins when both contain a value.
        return plain + secure
    }

    private fun safeRead(prefs: SharedPreferences?): Map<String, String> {
        if (prefs == null) return emptyMap()
        return runCatching {
            prefs.all.mapNotNull { (key, value) ->
                (value as? String)?.let { key to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ACCESS_EXPIRY = "access_expiry"
        const val KEY_USER = "user_json"
    }
}
