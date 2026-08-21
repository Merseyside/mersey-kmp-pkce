package com.merseyside.pkce

/** Состояние входа, за которым удобно наблюдать экранам. */
sealed interface AuthState {

    /** Хранилище ещё не читали. */
    data object Unknown : AuthState

    data object Unauthorized : AuthState

    data class Authorized(val tokens: TokenSet) : AuthState
}
