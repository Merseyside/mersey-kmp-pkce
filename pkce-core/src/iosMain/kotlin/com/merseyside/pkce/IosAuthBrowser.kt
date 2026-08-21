package com.merseyside.pkce

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Вход через `ASWebAuthenticationSession` — системное окно, у которого общие
 * куки с Safari. За счёт этого повторный вход обычно не спрашивает пароль,
 * а приложение не получает доступа к введённым данным.
 *
 * Адрес возврата должен быть на собственной схеме приложения, например
 * `shelfie://callback`: перехват https-адресов появился только в iOS 17.4,
 * а библиотека рассчитана на iOS 15. Схему нужно объявить в Info.plist
 * (`CFBundleURLTypes`), иначе система не вернёт управление приложению.
 */
class IosAuthBrowser : AuthBrowser {

    private val contextProvider = PresentationContextProvider()

    override suspend fun authorize(
        authUrl: String,
        redirectUri: String
    ): BrowserResult = suspendCancellableCoroutine { continuation ->
        val scheme = NSURLComponents(string = redirectUri).scheme
        check(scheme != null && scheme != "http" && scheme != "https") {
            "iOS needs a custom-scheme redirect URI (got '$redirectUri')"
        }

        val session = ASWebAuthenticationSession(
            uRL = NSURL(string = authUrl),
            callbackURLScheme = scheme
        ) { callbackUrl, error ->
            val result = when {
                callbackUrl != null -> BrowserResult.Redirect(callbackUrl.absoluteString.orEmpty())
                // Отмена приходит сюда же ошибкой — отличать её от сбоя незачем:
                // и то и другое означает «вход не состоялся»
                else -> BrowserResult.Cancelled
            }
            if (continuation.isActive) continuation.resume(result)
        }

        session.presentationContextProvider = contextProvider
        session.prefersEphemeralWebBrowserSession = false

        continuation.invokeOnCancellation { session.cancel() }
        session.start()
    }
}

/** Системе нужно окно, поверх которого показать вход. */
private class PresentationContextProvider :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {

    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor = UIApplication.sharedApplication.windows
        .filterIsInstance<UIWindow>()
        .firstOrNull { it.isKeyWindow() }
        ?: UIWindow()
}
