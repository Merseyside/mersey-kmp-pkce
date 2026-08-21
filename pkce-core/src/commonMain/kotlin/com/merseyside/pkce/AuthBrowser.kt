package com.merseyside.pkce

/**
 * Открывает страницу входа и возвращает адрес, на который сервис перенаправил
 * пользователя. Единственное, что реализуется под каждую платформу отдельно.
 *
 * Встроенный WebView для этого не годится: провайдеры входа его блокируют
 * (Google отвечает `disallowed_useragent`), а сессия браузера в нём не переиспользуется,
 * поэтому пароль спрашивают каждый раз. Правильный способ — системный браузер:
 * Custom Tabs на Android, ASWebAuthenticationSession на iOS, обычный переход в вебе.
 */
interface AuthBrowser {

    /**
     * @param authUrl адрес страницы авторизации со всеми параметрами.
     * @param redirectUri адрес возврата — по нему платформа понимает, что вход завершён.
     */
    suspend fun authorize(authUrl: String, redirectUri: String): BrowserResult
}

/** Чем закончилось открытие страницы входа. */
sealed interface BrowserResult {

    /** Сервис перенаправил на адрес возврата. Разбор параметров — дальше по цепочке. */
    data class Redirect(val url: String) : BrowserResult

    /** Пользователь закрыл окно, не завершив вход. */
    data object Cancelled : BrowserResult

    /**
     * Управление ушло целиком: страница браузера сменилась на страницу сервиса.
     * Результата в этом запуске приложения не будет — он придёт после возврата.
     */
    data object Deferred : BrowserResult
}
