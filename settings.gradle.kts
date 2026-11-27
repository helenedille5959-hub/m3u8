pluginManagement {
    repositories { google(); mavenCentral()
        maven { url = uri("https://jellyfin.org") }
; gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral()
        maven { url = uri("https://jellyfin.org") }
 }
}
rootProject.name = "JujuWan"
include(":app")
