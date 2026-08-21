package com.merseyside.pkce

import kotlinx.serialization.Serializable

/**
 * Выданные сервисом токены. Хранится целиком: без времени окончания срока
 * невозможно понять, нужно ли обновление, а без refresh-токена — нечем обновлять.
 *
 * @param expiresAtMillis момент, когда истекает токен доступа, в миллисекундах эпохи.
 */
@Serializable
data class TokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String,
    val expiresAtMillis: Long,
    val scopes: List<String> = emptyList()
) {

    /**
     * Запас в минуту нужен, чтобы токен не истёк между проверкой и приходом
     * запроса на сервер.
     */
    fun isExpired(nowMillis: Long, leewayMillis: Long = DEFAULT_LEEWAY_MILLIS): Boolean =
        nowMillis >= expiresAtMillis - leewayMillis

    /** Заголовок Authorization целиком, вместе со схемой. */
    val authorizationHeader: String
        get() = "$tokenType $accessToken"

    companion object {
        const val DEFAULT_LEEWAY_MILLIS: Long = 60_000
    }
}
