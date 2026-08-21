package com.merseyside.pkce

/**
 * В браузере отдельного окна входа нет: страница целиком уходит на сайт сервиса,
 * а приложение загружается заново уже по адресу возврата. Поэтому [authorize]
 * ничего не дожидается и возвращает [BrowserResult.Deferred].
 *
 * Приложение при старте должно проверить адрес и, если в нём есть ответ сервиса,
 * передать его в `PkceAuthService.completeRedirect`:
 * ```kotlin
 * val url = currentUrl()
 * if (authService.isRedirectUrl(url)) {
 *     authService.completeRedirect(url)
 *     WebAuthBrowser.clearRedirectParams()
 * }
 * ```
 */
class WebAuthBrowser : AuthBrowser {

    override suspend fun authorize(authUrl: String, redirectUri: String): BrowserResult {
        navigateTo(authUrl)
        return BrowserResult.Deferred
    }

    companion object {

        /** Текущий адрес страницы целиком, вместе с параметрами. */
        fun currentUrl(): String = readLocationHref()

        /**
         * Убирает код авторизации из адресной строки, не перезагружая страницу.
         * Иначе он остаётся в истории браузера и уедет в закладку или в чужие руки.
         *
         * Состояние истории при этом сохраняется как было: в него пишет навигация
         * приложения, и подмена его пустым объектом ломает разбор стека экранов.
         */
        fun clearRedirectParams(path: String = "/") {
            replaceStateWith(path)
        }
    }
}

private fun navigateTo(url: String) {
    js("window.location.href = url")
}

private fun readLocationHref(): String = js("window.location.href")

private fun replaceStateWith(path: String) {
    js("window.history.replaceState(window.history.state, document.title, path)")
}
