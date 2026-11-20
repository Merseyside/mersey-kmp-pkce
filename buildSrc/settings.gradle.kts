dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }

    versionCatalogs {
        create("catalogGradle") {
            from("io.github.merseyside:catalog-version-gradle:1.8.27")
        }
    }
}

// Явное имя: с включёнными projects без него кэш сборки зависит от имени папки
rootProject.name = "buildSrc"
