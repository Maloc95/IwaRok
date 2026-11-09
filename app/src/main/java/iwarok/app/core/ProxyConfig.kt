package iwarok.app.core

/** Which protocol the optional in-app proxy speaks. */
enum class ProxyType {
    HTTP,
    SOCKS,
}

/**
 * Optional user-configured proxy.
 *
 * Iwara is not reachable from mainland China without a proxy/VPN. The app cannot
 * provide a proxy service itself, but the user can point it at their own local or
 * LAN proxy (e.g. Clash/Mihomo on the phone: 127.0.0.1:7890) so that API, image
 * and video traffic all go through it.
 *
 * Saved in SharedPreferences and read once at process start; changes require an
 * app restart because [iwarok.AppGraph] builds its network clients
 * during Application.onCreate.
 */
data class ProxyConfig(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 0,
) {
    val isValid: Boolean
        get() = enabled && host.isNotBlank() && port in 1..65535

    val summary: String
        get() = if (isValid) "${type.name} $host:$port" else "未启用"
}
