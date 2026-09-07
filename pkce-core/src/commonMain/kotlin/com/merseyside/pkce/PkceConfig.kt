package com.merseyside.pkce

/**
 * Настройки конкретного сервиса авторизации. Ничего специфичного для отдельного
 * провайдера здесь нет: адреса, идентификатор приложения и запрашиваемые права.
 *
 * @param clientId идентификатор приложения, выданный сервисом. Не секрет —
 * в потоке PKCE секрет приложения не используется вовсе.
 * @param authorizationEndpoint адрес страницы, куда уходит пользователь.
 * @param tokenEndpoint адрес, на котором код меняется на токен.
 * @param redirectUri адрес возврата. Должен быть в точности таким же, как
 * зарегистрирован у провайдера: сравнение посимвольное.
 * @param callbackUri адрес, который перехватывает платформа, если он отличается от
 * [redirectUri]. Нужен, когда сервис принимает только https (так требует Spotify),
 * а вернуть управление приложению может лишь собственная схема: тогда по https-адресу
 * лежит страница, которая тут же перенаправляет на `myapp://callback`.
 * @param statePrefix своя часть параметра `state`, которую сервис вернёт нетронутой.
 * Нужна, когда адрес возврата один на несколько платформ: по ней страница возврата
 * понимает, куда отправить человека дальше. К префиксу через [STATE_SEPARATOR]
 * дописывается случайная часть — та, ради которой `state` и существует.
 * @param scopes запрашиваемые права.
 * @param scopeSeparator разделитель прав в запросе. Пробел — значение по умолчанию
 * из RFC 6749, но некоторые сервисы ждут запятую.
 * @param extraAuthParams дополнительные параметры страницы авторизации.
 */
data class PkceConfig(
    val clientId: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val redirectUri: String,
    val callbackUri: String? = null,
    val statePrefix: String? = null,
    val scopes: List<String> = emptyList(),
    val scopeSeparator: String = " ",
    val extraAuthParams: Map<String, String> = emptyMap()
) {
    init {
        require(clientId.isNotBlank()) { "clientId is blank" }
        require(redirectUri.isNotBlank()) { "redirectUri is blank" }

        // Иначе страница возврата не разберёт, где кончается префикс
        require(statePrefix?.contains(STATE_SEPARATOR) != true) {
            "statePrefix must not contain '$STATE_SEPARATOR'"
        }
    }

    /** Что ждёт платформа: собственный адрес перехвата, а если его нет — обычный. */
    val effectiveCallbackUri: String
        get() = callbackUri ?: redirectUri

    internal val scopeValue: String?
        get() = scopes.takeIf { it.isNotEmpty() }?.joinToString(scopeSeparator)

    companion object {

        /**
         * Разделитель между [statePrefix] и случайной частью `state`.
         *
         * Тильда не встречается в случайной части (она в base64url) и не требует
         * экранирования в адресе, поэтому обе половины разбираются однозначно.
         */
        const val STATE_SEPARATOR: String = "~"
    }
}
