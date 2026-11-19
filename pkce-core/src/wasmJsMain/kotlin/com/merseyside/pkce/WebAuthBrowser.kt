package com.merseyside.pkce

import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * Вход в браузере — во всплывающем окне, страница с приложением остаётся на месте.
 *
 * Раньше страница целиком уходила на сайт сервиса, и после возврата приложение
 * грузилось заново: заставка, а порой и скачивание wasm. Теперь сервис
 * возвращает на адрес возврата только всплывающее окно. Страница приложения в
 * нём замечает ответ ещё до загрузки wasm, передаёт его сюда по
 * [BroadcastChannel][AUTH_CHANNEL] и закрывается (см. `index.html` приложения).
 *
 * Канал, а не `window.opener`: страница сервиса может разорвать связь окон
 * заголовком Cross-Origin-Opener-Policy, а канал работает между любыми вкладками
 * одного сайта.
 *
 * Браузер заблокировал окно (Safari пускает его только сразу после нажатия) —
 * уходим на сервис всей страницей, как раньше: результат будет
 * [BrowserResult.Deferred], а ответ приложение разберёт при новом запуске:
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
        val answer = openAuthPopup(authUrl, AUTH_CHANNEL, POPUP_FLAG_KEY)
        if (answer == null) {
            navigateTo(authUrl)
            return BrowserResult.Deferred
        }

        val url = answer.await<JsString?>()?.toString()
        return if (url == null) BrowserResult.Cancelled else BrowserResult.Redirect(url)
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

/** Имя канала и ключ метки — те же, что в `index.html` приложения. */
private const val AUTH_CHANNEL = "shelfie-auth"
private const val POPUP_FLAG_KEY = "shelfieAuthPopup"

/**
 * Открывает окно входа и ждёт ответа. null — окно заблокировано.
 *
 * Обещание отдаёт адрес возврата или null, если окно закрыли без входа.
 * Закрытие по одному `popup.closed` не определить: после разрыва связи окон
 * (Cross-Origin-Opener-Policy) оно сразу говорит «закрыто», пока человек ещё
 * вводит пароль. Поэтому отменой считаем «окно закрыто, а наша страница снова
 * в фокусе и ответа нет полторы секунды».
 *
 * Метка в localStorage говорит странице во всплывающем окне, что она — окно
 * входа, а не обычный запуск с ответом в адресе.
 */
private fun openAuthPopup(url: String, channelName: String, flagKey: String): Promise<JsString?>? = js(
    """
    (function () {
        if (typeof BroadcastChannel !== 'function') return null;
        var popup = window.open(url, channelName, 'popup=yes,width=520,height=720');
        if (!popup) return null;

        // Прошлый вход так и не закончился, а окно уже открыто заново. Прошлое
        // ожидание гасим молча: ответ «отмена» стёр бы начатый только что вход
        if (window.__shelfieAuthAbort) window.__shelfieAuthAbort();
        try { localStorage.setItem(flagKey, String(Date.now())); } catch (e) {}

        return new Promise(function (resolve) {
            var channel = new BroadcastChannel(channelName);
            var done = false;
            var closedSeen = false;
            var cancelTimer = null;

            function stop() {
                done = true;
                channel.close();
                clearInterval(poll);
                clearTimeout(cancelTimer);
                window.removeEventListener('focus', onFocus);
            }

            function finish(result) {
                if (done) return;
                stop();
                window.__shelfieAuthAbort = null;
                try { localStorage.removeItem(flagKey); } catch (e) {}
                resolve(result);
            }

            function scheduleCancel() {
                clearTimeout(cancelTimer);
                cancelTimer = setTimeout(function () { finish(null); }, 1500);
            }

            function onFocus() {
                if (closedSeen) scheduleCancel();
            }

            channel.onmessage = function (event) {
                var data = event.data || {};
                if (data.type !== 'redirect' || typeof data.url !== 'string') return;
                channel.postMessage({ type: 'ack' });
                finish(data.url);
            };

            window.addEventListener('focus', onFocus);
            var poll = setInterval(function () {
                if (closedSeen || !popup.closed) return;
                closedSeen = true;
                if (document.hasFocus()) scheduleCancel();
            }, 500);

            window.__shelfieAuthAbort = function () { if (!done) stop(); };
        });
    })()
    """
)

private fun navigateTo(url: String) {
    js("window.location.href = url")
}

private fun readLocationHref(): String = js("window.location.href")

private fun replaceStateWith(path: String) {
    js("window.history.replaceState(window.history.state, document.title, path)")
}
