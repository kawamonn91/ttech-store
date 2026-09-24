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
include(":family-quiz")
include(":family-todo")
include(":fasting-timer")
include(":fishing-log")
include(":flashcards")
include(":furusato-nozei")
include(":goshuincho")
include(":handmade-pricing")
include(":image-batch")
include(":savings-goal")
include(":water-tracker")
include(":weight-log")
include(":sleep-log")
include(":workout-log")
include(":reading-log")
include(":movie-log")
include(":tasting-notes")
include(":kids-allowance")
include(":subscription-manager")
include(":meeting-notes")
include(":one-on-one-log")
include(":word-quiz")
include(":property-checklist")
include(":restaurant-checklist")
include(":tutor-progress")
include(":salon-booking")
include(":room-checkin")
include(":receipt-tracker")
