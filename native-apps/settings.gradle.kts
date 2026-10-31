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
    }
}

rootProject.name = "ttech-apps"

// 新しいアプリを追加するときは、ここに1行足すだけでよい(build.gradle.kts はテンプレートをコピーする)
include(":common")
include(":track-common")
include(":pocket-dev")
include(":weight-log")
include(":ttech-admin")
include(":drive-record")
include(":run-tracker")
include(":trip-shiori")
include(":travel-wishlist")
include(":idea-memo")
include(":navi")
include(":bike-navi")
include(":attendance-count")
