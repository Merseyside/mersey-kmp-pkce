package com.merseyside.pkce

/** Чем закончилась попытка входа. */
sealed interface AuthResult {

    data class Success(val tokens: TokenSet) : AuthResult

    /** Пользователь закрыл окно входа. */
    data object Cancelled : AuthResult

    /**
     * Веб: страница ушла на сайт сервиса, и этот запуск приложения на этом
     * заканчивается. Результат придёт после возврата — в [PkceAuthService.completeRedirect].
     */
    data object Redirecting : AuthResult

    data class Failure(val error: PkceError) : AuthResult
}
