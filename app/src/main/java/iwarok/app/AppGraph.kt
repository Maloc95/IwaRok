package iwarok.app

import android.content.Context
import iwarok.app.core.IwaraRepository
import iwarok.app.core.ProxyConfig
import iwarok.app.core.ProxyType
import iwarok.app.data.DefaultIwaraRepository
import iwarok.app.data.ProxySettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Process-wide dependency holder, created by [IwarokApp].
 *
 * The OkHttp clients are shared by the API layer, Coil and ExoPlayer so that all
 * long-lived HTTP connections share one connection pool and one connection policy.
 *
 * The optional user proxy is read synchronously here (before the repository is
 * constructed) so API, image and media traffic all use the same route.
 */
class AppGraph(context: Context) {

    val appContext: Context = context.applicationContext

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** User-configured proxy; read once at process start, changes need a restart. */
    val proxyConfig: ProxyConfig = ProxySettingsStore.load(appContext)

    private val javaProxy: Proxy? = if (proxyConfig.isValid) {
        val type = if (proxyConfig.type == ProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
        runCatching {
            Proxy(type, InetSocketAddress(proxyConfig.host.trim(), proxyConfig.port))
        }.getOrNull()
    } else {
        null
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply { javaProxy?.let { proxy(it) } }
        .build()

    /**
     * Dedicated client for ExoPlayer media streams.
     *
     * The API client above has a 60 s overall call timeout. Applying that to a
     * multi-minute video download aborts the source part-way through playback
     * (the classic "video stops / cannot play" symptom). Media requests must be
     * allowed to run for the whole stream, so this client disables the overall
     * call timeout while keeping sensible connect/read timeouts.
     */
    val mediaHttpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    val repository: IwaraRepository = DefaultIwaraRepository(appContext, httpClient)
}
