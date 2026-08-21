package com.merseyside.pkce

import android.content.Context

/**
 * Открывает вход в Custom Tabs — вкладке системного браузера поверх приложения.
 *
 * Приложение должно объявить в своём манифесте адрес возврата, дописав фильтр
 * к активности библиотеки:
 * ```xml
 * <activity android:name="com.merseyside.pkce.PkceAuthActivity" tools:node="merge">
 *     <intent-filter android:autoVerify="true">
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="https" android:host="example.com" android:path="/callback" />
 *     </intent-filter>
 * </activity>
 * ```
 */
class AndroidAuthBrowser(context: Context) : AuthBrowser {

    private val appContext = context.applicationContext

    override suspend fun authorize(authUrl: String, redirectUri: String): BrowserResult {
        val awaitResult = PkceRedirectBus.start()
        appContext.startActivity(PkceAuthActivity.createIntent(appContext, authUrl))
        return awaitResult.await()
    }
}
