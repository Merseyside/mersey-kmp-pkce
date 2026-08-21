import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(catalogPlugins.plugins.kotlin.multiplatform)
    alias(catalogPlugins.plugins.android.kotlin.multiplatform.library)
    alias(catalogPlugins.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.merseyside.pkce"
        compileSdk = androidLibs.versions.compileSdk.get().toInt()
        minSdk = androidLibs.versions.compileMinSdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        all {
            compilerOptions {
                freeCompilerArgs = listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    // Base64 из stdlib помечен экспериментальным
                    "-opt-in=kotlin.ExperimentalStdlibApi",
                    "-Xexpect-actual-classes",
                )
            }
        }

        commonMain.dependencies {
            implementation(common.coroutines)
            implementation(common.serialization)

            // api: HttpClient передаёт вызывающая сторона, тип виден в её коде
            api(multiplatformLibs.ktor.core)
        }

        androidMain.dependencies {
            // Custom Tabs: вход открывается в системном браузере поверх приложения
            implementation(androidLibs.browser)
        }

        // Взаимодействие с JS в Wasm пока помечено экспериментальным
        wasmJsMain.get().languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")

        commonTest.dependencies {
            implementation(common.kotlin.test)
        }
    }
}
