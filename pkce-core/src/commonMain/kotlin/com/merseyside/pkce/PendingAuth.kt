package com.merseyside.pkce

import kotlinx.serialization.Serializable

/**
 * Начатая, но не завершённая авторизация. Хранится до возврата из браузера:
 * в вебе за это время приложение успевает полностью перезагрузиться, на мобильных
 * система может убить процесс.
 */
@Serializable
data class PendingAuth(
    val verifier: String,
    val state: String,
    val redirectUri: String
)
