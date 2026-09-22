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
include(":warikan")
include(":pomodoro-timer")
include(":date-calculator")
include(":cooking-units")
include(":color-palette")
include(":qr-generator")
include(":business-card-contacts")
include(":business-card-maker")
include(":camp-checklist")
include(":baby-log")
include(":child-growth")
include(":cycle-tracker")
include(":daily-fortune")
include(":exam-countdown")
include(":expense-tracker")
