package com.merseyside.pkce

import kotlinx.coroutines.CompletableDeferred

/**
 * Передаёт результат из [PkceAuthActivity] тому, кто ждёт его в корутине.
 *
 * Если систему заставили выгрузить процесс, пока пользователь был в браузере,
 * ожидающая корутина умирает вместе с ним — результат прийти некуда, и вход
 * придётся начать заново. Токены при этом не теряются: их пишет только успешный обмен.
 */
internal object PkceRedirectBus {

    private var pending: CompletableDeferred<BrowserResult>? = null

    fun start(): CompletableDeferred<BrowserResult> =
        CompletableDeferred<BrowserResult>().also {
            pending?.complete(BrowserResult.Cancelled)
            pending = it
        }

    fun publish(result: BrowserResult) {
        pending?.complete(result)
        pending = null
    }
}
