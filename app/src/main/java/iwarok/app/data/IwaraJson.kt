package iwarok.app.data

import iwarok.app.core.IwaraUser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared kotlinx.serialization configuration for every Iwara wire payload.
 *
 * The API evolves without notice and contains content-kind specific fields, so
 * unknown keys are ignored, malformed input is tolerated where possible and
 * null literals for non-nullable fields fall back to defaults.
 */
internal val iwaraJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/* ------------------------------------------------------------------------- *
 * Authentication wire models (only the fields we actually consume).
 * ------------------------------------------------------------------------- */

@Serializable
internal data class LoginRequest(
    val email: String,
    val password: String,
)

/** `POST /user/login` response: the long-lived refresh token. */
@Serializable
internal data class LoginResponse(
    val token: String = "",
)

/**
 * `POST /user/token` response. Upstream currently returns `{accessToken}`; the
 * expiry fields are optional because they are not guaranteed to be present.
 */
@Serializable
internal data class TokenResponse(
    val accessToken: String = "",
    @SerialName("expiresIn") val expiresIn: Long? = null,
    @SerialName("expiresAt") val expiresAt: Long? = null,
)

/** `GET /user` response. */
@Serializable
internal data class UserEnvelope(
    val user: IwaraUser = IwaraUser(),
)
