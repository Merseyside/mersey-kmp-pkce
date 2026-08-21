package com.merseyside.pkce

/** Причина, по которой вход или обновление токена не удались. */
sealed class PkceError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Сервис вернул ошибку на странице авторизации или в ответ на обмен кода. */
    class ServerError(
        val error: String,
        val description: String? = null
    ) : PkceError(listOfNotNull(error, description).joinToString(": "))

    /** Адрес возврата пришёл без кода и без ошибки — так быть не должно. */
    class MalformedRedirect(url: String) : PkceError("Redirect has neither code nor error: $url")

    /**
     * Параметр state в ответе не совпал с отправленным. Признак подмены ответа
     * или чужого редиректа: результат надо выбросить.
     */
    object StateMismatch : PkceError("Redirect state does not match the request")

    /** Начатой авторизации нет: редирект пришёл, а верификатор потерян. */
    object NoPendingAuth : PkceError("No pending authorization found")

    /** Обновлять нечем: сервис не выдал refresh-токен либо пользователь вышел. */
    object NoRefreshToken : PkceError("No refresh token available")

    /** Сеть, разбор ответа и прочее неожиданное. */
    class Unexpected(cause: Throwable) : PkceError(cause.message ?: "Unexpected error", cause)
}
