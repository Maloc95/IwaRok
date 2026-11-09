package iwarok.app.core

/**
 * Static configuration shared by every layer.
 *
 * The salt is the value used by Iwara's web frontend to sign the quality-manifest
 * request (X-Version = SHA1("<fileUuid>_<expires>_<salt>")). It rotates rarely;
 * [DefaultIwaraRepository] falls back to this constant and may refresh it from the
 * website when a low-quality-only manifest is returned.
 */
object IwaraConfig {
    const val API_PRIMARY = "https://apiq.iwara.tv"
    const val API_FALLBACK = "https://api.iwara.tv"
    const val SITE_ORIGIN = "https://www.iwara.tv"
    const val IMAGE_HOST = "https://i.iwara.tv"
    const val DEFAULT_SALT = "mSvL05GfEmeEmsEYfGCnVpEjYgTJraJN"
    const val SITE_NAME = "www.iwara.tv"
    const val SITE_LABEL = "IwaRok"

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    const val PAGE_SIZE = 20
    const val PREFS_NAME = "iwarok_prefs"
    const val SECURE_PREFS_NAME = "iwarok_session"

    fun webVideoUrl(videoId: String): String = "$SITE_ORIGIN/video/$videoId"
    fun webUserUrl(username: String): String = "$SITE_ORIGIN/profile/$username"
}
