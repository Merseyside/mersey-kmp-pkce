package com.merseyside.pkce

import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64

/**
 * Вход по схеме Authorization Code + PKCE (RFC 7636).
 *
 * Порядок такой: приложение генерирует верификатор, отправляет пользователя на
 * страницу сервиса с хешем верификатора, получает обратно одноразовый код и меняет
 * его на токен, предъявив верификатор. Секрет приложения не нужен — потому эта
 * схема и годится для клиентских приложений, откуда секрет всё равно можно достать.
 *
 * @param browser способ открыть страницу входа — своя реализация на каждой платформе.
 * @param storage где хранить токены; библиотека не выбирает хранилище за приложение.
 */
class PkceAuthService(
    private val config: PkceConfig,
    httpClient: HttpClient,
    private val storage: PkceTokenStorage,
    private val browser: AuthBrowser
) {

    private val tokenApi = TokenApi(httpClient, config)

    /** Обновление токена делается по одному за раз: иначе два запроса сожгут refresh-токен. */
    private val refreshMutex = Mutex()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Прочитать сохранённые токены. Вызывается при старте приложения. */
    suspend fun loadState(): AuthState {
        val tokens = runCatching { storage.getTokens() }.getOrNull()
        return updateState(tokens)
    }

    /**
     * Начать вход.
     *
     * На Android и iOS возвращается результат целиком: окно входа живёт поверх
     * приложения. В браузере страница уходит на сайт сервиса, поэтому ответом будет
     * [AuthResult.Redirecting], а настоящий результат придёт в [completeRedirect]
     * при следующем запуске.
     */
    suspend fun login(): AuthResult = runCatchingAuth {
        val codes = PkceCodes.generate()
        val state = randomState()

        storage.savePendingAuth(
            PendingAuth(verifier = codes.verifier, state = state, redirectUri = config.redirectUri)
        )

        val callbackUri = config.effectiveCallbackUri
        when (val result = browser.authorize(buildAuthUrl(codes, state), callbackUri)) {
            is BrowserResult.Redirect -> completeRedirect(result.url)
            BrowserResult.Deferred -> AuthResult.Redirecting
            BrowserResult.Cancelled -> {
                storage.savePendingAuth(null)
                AuthResult.Cancelled
            }
        }
    }

    /**
     * Завершить вход по адресу возврата.
     *
     * В вебе вызывается при старте приложения, если в адресе есть параметр `code`.
     * На мобильных вызывается изнутри [login].
     */
    suspend fun completeRedirect(redirectUrl: String): AuthResult = runCatchingAuth {
        val pending = storage.getPendingAuth() ?: return@runCatchingAuth fail(PkceError.NoPendingAuth)

        val params = Url(redirectUrl).parameters
        params["error"]?.let { error ->
            storage.savePendingAuth(null)
            return@runCatchingAuth fail(
                PkceError.ServerError(error, params["error_description"])
            )
        }

        // Сверка state отсекает чужой или подменённый редирект
        if (params["state"] != pending.state) {
            storage.savePendingAuth(null)
            return@runCatchingAuth fail(PkceError.StateMismatch)
        }

        val code = params["code"]
            ?: return@runCatchingAuth fail(PkceError.MalformedRedirect(redirectUrl))

        val tokens = tokenApi.exchangeCode(code = code, verifier = pending.verifier)
        storage.savePendingAuth(null)
        storage.saveTokens(tokens)
        updateState(tokens)

        AuthResult.Success(tokens)
    }

    /**
     * Токен доступа, пригодный прямо сейчас: просроченный обновляется сам.
     * Возвращает null, если пользователь не входил или обновить не удалось.
     */
    suspend fun getValidAccessToken(): String? {
        val tokens = storage.getTokens() ?: return null
        if (!tokens.isExpired(currentTimeMillis())) return tokens.accessToken

        return when (val result = refreshTokens()) {
            is AuthResult.Success -> result.tokens.accessToken
            else -> null
        }
    }

    /** Обновить токен по refresh-токену. */
    suspend fun refreshTokens(): AuthResult = refreshMutex.withLock {
        runCatchingAuth {
            val current = storage.getTokens()
            val refreshToken = current?.refreshToken
                ?: return@runCatchingAuth fail(PkceError.NoRefreshToken)

            // Пока ждали очередь, другой вызов мог всё обновить
            if (current.isExpired(currentTimeMillis()).not()) {
                return@runCatchingAuth AuthResult.Success(current)
            }

            val tokens = tokenApi.refresh(refreshToken)
            storage.saveTokens(tokens)
            updateState(tokens)

            AuthResult.Success(tokens)
        }
    }

    /** Забыть токены. Сессию в браузере это не трогает: там свои куки. */
    suspend fun logout() {
        storage.saveTokens(null)
        storage.savePendingAuth(null)
        updateState(null)
    }

    /** Есть ли в адресе признаки ответа от сервиса авторизации. */
    fun isRedirectUrl(url: String): Boolean {
        val params = runCatching { Url(url).parameters }.getOrNull() ?: return false
        return params.contains("code") || params.contains("error")
    }

    private fun buildAuthUrl(codes: PkceCodes, state: String): String =
        URLBuilder(config.authorizationEndpoint).apply {
            parameters.append("client_id", config.clientId)
            parameters.append("response_type", "code")
            parameters.append("redirect_uri", config.redirectUri)
            parameters.append("code_challenge_method", codes.method)
            parameters.append("code_challenge", codes.challenge)
            parameters.append("state", state)
            config.scopeValue?.let { parameters.append("scope", it) }
            config.extraAuthParams.forEach { (key, value) -> parameters.append(key, value) }
        }.buildString()

    /**
     * Случайная часть `state` — та, что защищает от подмены ответа. Если приложение
     * задало [PkceConfig.statePrefix], он идёт впереди: сервис вернёт `state`
     * нетронутым, и по префиксу страница возврата поймёт, куда отправлять дальше.
     * Сверяется потом всё равно строка целиком.
     */
    private fun randomState(): String {
        val random = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(secureRandomBytes(16))

        val prefix = config.statePrefix ?: return random
        return prefix + PkceConfig.STATE_SEPARATOR + random
    }

    private fun updateState(tokens: TokenSet?): AuthState {
        val state = tokens?.let { AuthState.Authorized(it) } ?: AuthState.Unauthorized
        _authState.value = state
        return state
    }

    private fun fail(error: PkceError): AuthResult = AuthResult.Failure(error)

    /** Наружу летит только [AuthResult]: разбирать исключения экранам незачем. */
    private inline fun runCatchingAuth(block: () -> AuthResult): AuthResult = try {
        block()
    } catch (error: PkceError) {
        AuthResult.Failure(error)
    } catch (error: Throwable) {
        AuthResult.Failure(PkceError.Unexpected(error))
    }
}
