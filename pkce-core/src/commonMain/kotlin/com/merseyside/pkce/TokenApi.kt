package com.merseyside.pkce

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Запросы к token endpoint. Ответ разбирается своим Json, а не через
 * ContentNegotiation: клиент приходит снаружи, и навязывать ему настройку
 * сериализации библиотека не должна.
 */
internal class TokenApi(
    private val client: HttpClient,
    private val config: PkceConfig
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exchangeCode(code: String, verifier: String): TokenSet = request(
        Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", config.redirectUri)
            append("client_id", config.clientId)
            append("code_verifier", verifier)
        }
    )

    /**
     * Ответ на обновление может не содержать нового refresh-токена — тогда
     * продолжает действовать прежний.
     */
    suspend fun refresh(refreshToken: String): TokenSet = request(
        Parameters.build {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
            append("client_id", config.clientId)
        },
        previousRefreshToken = refreshToken
    )

    private suspend fun request(
        form: Parameters,
        previousRefreshToken: String? = null
    ): TokenSet {
        val response = client.submitForm(url = config.tokenEndpoint, formParameters = form)
        val body = response.bodyAsText()

        if (!response.status.isSuccess()) {
            val error = runCatching { json.decodeFromString<ErrorData>(body) }.getOrNull()
            throw PkceError.ServerError(
                error = error?.error ?: "http_${response.status.value}",
                description = error?.description ?: body.takeIf { it.isNotBlank() }
            )
        }

        val data = json.decodeFromString<TokenData>(body)
        return TokenSet(
            accessToken = data.accessToken,
            refreshToken = data.refreshToken ?: previousRefreshToken,
            tokenType = data.tokenType,
            expiresAtMillis = currentTimeMillis() + data.expiresInSeconds * 1000L,
            scopes = data.scope?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }

    @Serializable
    private data class TokenData(
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String = "Bearer",
        @SerialName("expires_in") val expiresInSeconds: Long = 3600,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("scope") val scope: String? = null
    )

    @Serializable
    private data class ErrorData(
        @SerialName("error") val error: String,
        @SerialName("error_description") val description: String? = null
    )
}
