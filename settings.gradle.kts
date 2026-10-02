pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Private Twyn SDK registry (needs a GitHub token with read:packages).
        maven {
            name = "TwynSdkDist"
            url = uri("https://maven.pkg.github.com/twyn-internal/twyn-sdk-dist")
            credentials {
                username = providers.gradleProperty("twynUser").orNull
                    ?: System.getenv("TWYN_MAVEN_USER")
                password = providers.gradleProperty("twynToken").orNull
                    ?: System.getenv("TWYN_MAVEN_TOKEN")
            }
        }
    }
}

rootProject.name = "twyn-sdk-android-sample"
include(":sample")
