# mersey-kmp-pkce

Вход по OAuth 2.0 с PKCE для Kotlin Multiplatform: **Android, iOS, Web (Wasm)**.
Ничего специфичного для конкретного сервиса внутри нет — адреса и права передаются в настройках.

## Зачем PKCE

Обычный OAuth требует секрет приложения. В клиентском приложении секрета быть не может:
из APK и из JS-бандла его достают за минуту. PKCE (Proof Key for Code Exchange, RFC 7636)
решает это так:

1. приложение придумывает случайную строку — **верификатор**, и держит её у себя;
2. на страницу входа отправляет не строку, а её SHA-256;
3. пользователь разрешает доступ, сервис возвращает **одноразовый код**;
4. код меняется на токен, и вместе с кодом предъявляется исходный верификатор.

Перехваченный код бесполезен: без верификатора его не обменять.

## Что внутри

```
pkce-core/
├── commonMain/
│   ├── PkceConfig.kt          адреса, clientId, адрес возврата, права
│   ├── PkceCodes.kt           верификатор и его хеш
│   ├── Sha256.kt              хеш общим кодом (в браузере встроенный — асинхронный)
│   ├── PkceAuthService.kt     весь поток: вход, обмен, обновление, выход
│   ├── TokenApi.kt            запросы к token endpoint
│   ├── TokenSet.kt            токены и срок их жизни
│   ├── PkceTokenStorage.kt    интерфейс хранилища + реализация в памяти
│   ├── AuthBrowser.kt         интерфейс «открой страницу входа»
│   └── AuthResult.kt, AuthState.kt, PkceError.kt, PendingAuth.kt
├── androidMain/               Custom Tabs + activity-посредник
├── iosMain/                   ASWebAuthenticationSession
└── wasmJsMain/                переход по адресу и разбор возврата
```

Приложение отдаёт библиотеке две вещи: **HttpClient** (свой, со своими движками и логами)
и **хранилище токенов**. Навязывать ни то ни другое библиотека не должна: у каждого проекта
свои требования к безопасности хранения.

## Использование

```kotlin
val config = PkceConfig(
    clientId = "…",
    authorizationEndpoint = "https://accounts.spotify.com/authorize",
    tokenEndpoint = "https://accounts.spotify.com/api/token",
    redirectUri = "http://127.0.0.1:8080/callback",
    scopes = listOf("user-library-read")
)

val authService = PkceAuthService(
    config = config,
    httpClient = httpClient,
    storage = myTokenStorage,
    browser = platformBrowser   // AndroidAuthBrowser / IosAuthBrowser / WebAuthBrowser
)

when (val result = authService.login()) {
    is AuthResult.Success -> openApp()
    AuthResult.Redirecting -> Unit          // веб: страница ушла, результат будет после возврата
    AuthResult.Cancelled -> Unit
    is AuthResult.Failure -> showError(result.error)
}

// Доступ к API: просроченный токен обновляется сам
val token = authService.getValidAccessToken()
```

## Что нужно от каждой платформы

### Android

Custom Tabs — вкладка системного браузера поверх приложения. Встроенный WebView намеренно
не используется: провайдеры входа его блокируют (Google отвечает `disallowed_useragent`),
а куки в нём не общие с браузером, поэтому пароль спрашивают каждый раз.

Приложение дописывает адрес возврата к активности библиотеки в своём манифесте:

```xml
<activity android:name="com.merseyside.pkce.PkceAuthActivity" tools:node="merge">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="example.com" android:path="/callback" />
    </intent-filter>
</activity>
```

Если система выгрузит процесс, пока пользователь был в браузере, результат вернуть некуда —
вход придётся начать заново. Токены при этом не теряются: пишет их только успешный обмен.

### iOS

`ASWebAuthenticationSession` — системное окно с общими с Safari куками. Приложение не видит,
что пользователь вводит.

Адрес возврата должен быть на собственной схеме (`myapp://callback`) и объявлен в `Info.plist`
в `CFBundleURLTypes`. Перехват https-адресов в этом API появился только в iOS 17.4, а
библиотека рассчитана на iOS 15.

### Web

Отдельного окна нет: страница целиком уходит на сайт сервиса, приложение загружается заново
уже по адресу возврата. Поэтому `login()` возвращает `AuthResult.Redirecting`, а при старте
приложение само проверяет адрес:

```kotlin
val url = WebAuthBrowser.currentUrl()
if (authService.isRedirectUrl(url)) {
    authService.completeRedirect(url)
    WebAuthBrowser.clearRedirectParams()   // убрать код из адресной строки
}
```

Сервер должен отдавать `index.html` на адрес возврата, иначе браузер получит 404 вместо приложения.

## Защита от подмены ответа

Вместе с запросом уходит случайный параметр `state`. Ответ с чужим или отсутствующим `state`
отбрасывается — иначе злоумышленник мог бы подсунуть свой код авторизации и привязать
свой аккаунт к чужому сеансу.

Верификатор и `state` живут в хранилище до возврата из браузера: в вебе приложение за это
время перезагружается полностью, на мобильных процесс может быть выгружен.

## Подключение

```kotlin
// settings.gradle.kts
includeBuild("mersey-kmp-pkce")

// build.gradle.kts модуля
implementation("io.github.merseyside:pkce-core:0.1.0")
```

## Проверка

```sh
./gradlew -p mersey-kmp-pkce :pkce-core:assemble              # все три платформы
./gradlew -p mersey-kmp-pkce :pkce-core:iosSimulatorArm64Test # тесты
```

Тесты проверяют SHA-256 эталонными векторами FIPS 180-4 и пару «верификатор — вызов»
примером из приложения B к RFC 7636.
