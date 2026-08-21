package com.merseyside.pkce

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Невидимый посредник между приложением и системным браузером.
 *
 * Нужен из-за того, что переход в браузер и возврат из него — два независимых
 * события: приложение уходит в фон, а результат приходит новым Intent'ом.
 * Логика опирается на порядок вызовов жизненного цикла:
 *
 * 1. первый `onResume` — открываем вкладку браузера и уходим в фон;
 * 2. `onNewIntent` — сервис перенаправил пользователя обратно, адрес лежит в Intent;
 * 3. второй `onResume` — если адреса нет, значит вкладку просто закрыли: это отмена.
 *
 * `launchMode="singleTask"` обязателен: без него возврат создал бы второй экземпляр,
 * и шаг 2 не сработал бы.
 */
internal class PkceAuthActivity : Activity() {

    private var browserLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browserLaunched = savedInstanceState?.getBoolean(KEY_BROWSER_LAUNCHED) ?: false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Адрес возврата приходит сюда; onResume разберёт его следующим шагом
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        if (!browserLaunched) {
            val authUrl = intent.getStringExtra(EXTRA_AUTH_URL)
            if (authUrl == null) {
                PkceRedirectBus.publish(BrowserResult.Cancelled)
                finish()
                return
            }

            browserLaunched = true
            runCatching { openCustomTab(authUrl) }
                .onFailure {
                    PkceRedirectBus.publish(BrowserResult.Cancelled)
                    finish()
                }
            return
        }

        val redirect = intent.data
        PkceRedirectBus.publish(
            if (redirect != null) BrowserResult.Redirect(redirect.toString())
            else BrowserResult.Cancelled
        )
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_BROWSER_LAUNCHED, browserLaunched)
    }

    private fun openCustomTab(authUrl: String) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, Uri.parse(authUrl))
    }

    companion object {
        private const val EXTRA_AUTH_URL = "pkce_auth_url"
        private const val KEY_BROWSER_LAUNCHED = "pkce_browser_launched"

        fun createIntent(context: Context, authUrl: String): Intent =
            Intent(context, PkceAuthActivity::class.java).apply {
                putExtra(EXTRA_AUTH_URL, authUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
