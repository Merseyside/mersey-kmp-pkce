package com.merseyside.pkce

/**
 * Где библиотека держит токены и незавершённую авторизацию.
 *
 * Реализацию даёт приложение: библиотека намеренно не выбирает за него хранилище,
 * потому что требования разные — защищённые настройки на Android, Keychain на iOS,
 * localStorage в браузере. Всё, что сюда попадает, — секреты; обычный файл настроек
 * для них не годится.
 */
interface PkceTokenStorage {

    suspend fun saveTokens(tokens: TokenSet?)

    suspend fun getTokens(): TokenSet?

    suspend fun savePendingAuth(pendingAuth: PendingAuth?)

    suspend fun getPendingAuth(): PendingAuth?
}

/** Хранилище в памяти: для тестов и для входа, который не должен переживать перезапуск. */
class InMemoryTokenStorage : PkceTokenStorage {

    private var tokens: TokenSet? = null
    private var pendingAuth: PendingAuth? = null

    override suspend fun saveTokens(tokens: TokenSet?) {
        this.tokens = tokens
    }

    override suspend fun getTokens(): TokenSet? = tokens

    override suspend fun savePendingAuth(pendingAuth: PendingAuth?) {
        this.pendingAuth = pendingAuth
    }

    override suspend fun getPendingAuth(): PendingAuth? = pendingAuth
}
