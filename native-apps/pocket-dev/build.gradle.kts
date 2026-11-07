import java.util.Properties

/** リリース署名。keystore.properties が無い環境ではデバッグ署名のままビルドが通る(他アプリと同じ方式) */
val releaseSigning: Properties? = rootProject.file("pocket-dev/keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

/**
 * Google ドライブ直結のログインに使う OAuth クライアント。公開リポジトリには入れない（README を参照）。
 * 無い環境（CI など）でもデバッグ版はビルドでき、アプリの Drive タブはログインできないと表示する
 */
val driveOAuth: Properties? = rootProject.file("pocket-dev/google-oauth.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ttech.pocketdev"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ttech.pocketdev"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.2"

        buildConfigField("String", "DRIVE_CLIENT_ID", "\"${driveOAuth?.getProperty("clientId").orEmpty()}\"")
        buildConfigField("String", "DRIVE_CLIENT_SECRET", "\"${driveOAuth?.getProperty("clientSecret").orEmpty()}\"")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file("pocket-dev/" + releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // Google ドライブ直結で使う rclone（librclone.so）を nativeLibraryDir に展開させる（APK 内のままでは exec できない）
            useLegacyPackaging = true
            // ビルド済みの実行ファイル（scripts/build-rclone.sh で -s -w を付けて作る）なので、NDK の strip にかけない
            keepDebugSymbols += "**/librclone.so"
        }
    }

    testOptions {
        // ConnectProxy などが android.util.Log を呼ぶため
        unitTests.isReturnDefaultValues = true
    }

    androidResources {
        localeFilters += listOf("ja", "en")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// リリース版を、Google にログインできないまま配布しないように止める
val hasDriveOAuth = driveOAuth != null
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    val configured = hasDriveOAuth
    doFirst {
        check(configured) { "pocket-dev/google-oauth.properties がありません（pocket-dev の README の「Google のログインの設定」を参照）" }
    }
}

// 隣の pocket-dev リポジトリの web/ アセットをアプリ側の assets/pocket-dev/web/ に同期する開発用タスク
val syncWebAssets = tasks.register<Sync>("syncWebAssets") {
    from(rootProject.file("../../pocket-dev/web"))
    into(layout.projectDirectory.dir("src/main/assets/pocket-dev/web"))
}
tasks.configureEach {
    if (name != "syncWebAssets") mustRunAfter(syncWebAssets)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Google のログインの画面を、アプリと同じタスクで開く（許可のあと × で戻れる）
    implementation("androidx.browser:browser:1.8.0")

    // SSH & ローカルHTTP/WebSocketブリッジ
    implementation("com.github.mwiede:jsch:0.2.23")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
