package iwarok.app.data

import android.content.Context
import iwarok.app.core.ProxyConfig
import iwarok.app.core.ProxyType

/**
 * Synchronous storage for the optional proxy configuration.
 *
 * It must be readable inside `Application.onCreate` before any network client is
 * built, which is why this uses plain SharedPreferences instead of DataStore.
 */
object ProxySettingsStore {

    private const val PREFS_NAME = "iwarok_network"
    private const val KEY_ENABLED = "proxy_enabled"
    private const val KEY_TYPE = "proxy_type"
    private const val KEY_HOST = "proxy_host"
    private const val KEY_PORT = "proxy_port"

    fun load(context: Context): ProxyConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val type = runCatching {
            ProxyType.valueOf(prefs.getString(KEY_TYPE, ProxyType.HTTP.name) ?: ProxyType.HTTP.name)
        }.getOrDefault(ProxyType.HTTP)
        return ProxyConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            type = type,
            host = prefs.getString(KEY_HOST, "").orEmpty(),
            port = prefs.getInt(KEY_PORT, 0),
        )
    }

    fun save(context: Context, config: ProxyConfig): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_TYPE, config.type.name)
            .putString(KEY_HOST, config.host.trim())
            .putInt(KEY_PORT, config.port)
            .commit()
    }
}
