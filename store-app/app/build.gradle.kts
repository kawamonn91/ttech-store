import java.util.Properties

/**
 * API のベース URL は local.properties の storeApiBase で上書きできる
 * (エミュレータからローカルの Next.js に繋ぐ場合は http://10.0.2.2:3000/api/v1)。
 * 未設定なら本番を向く。
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val storeApiBase: String = localProperties.getProperty("storeApiBase") ?: "https://store.kawamonn.com/api/v1"

/** マイページの管理者操作(公開・却下)で使う、Webの管理APIのベースURL(/api/v1 を含まない方) */
val storeWebBase: String = localProperties.getProperty("storeWebBase") ?: "https://store.kawamonn.com"

/**
 * Google ログイン(Credential Manager)用の OAuth クライアントID(ウェブアプリケーション種別。
 * Supabase の Google プロバイダー設定と同じもの)。local.properties の googleWebClientId で設定する。
 * 未設定でもビルドは通り、その場合はGoogleログインのボタンがエラーを表示する。
 */
val googleWebClientId: String = localProperties.getProperty("googleWebClientId") ?: ""

/**
 * Supabase の URL と anon キー。anon キーは「公開してよい」設計の値(RLSで保護される)で、
 * Web版でもブラウザに配信している値と同じもの。local.properties で上書きもできる。
 */
val supabaseUrl: String = localProperties.getProperty("supabaseUrl") ?: "https://bniehgjuylkgxbstmjdh.supabase.co"
val supabaseAnonKey: String = localProperties.getProperty("supabaseAnonKey")
    ?: "sb_publishable_JmdR3qk3rytSemnuaeOI_g_Z9SLuiaQ"

/**
 * リリース署名の情報。keystore.properties は .gitignore 済みで鍵はリポジトリに入らない。
 * ファイルが無い環境ではデバッグ署名のままビルドが通る。
 *
 * 重要: このストアアプリは自分自身の更新もストア経由で配る。署名鍵を変えると
 * 既存ユーザーが更新できなくなるため、この鍵は失くさず・変えないこと。
 */
val releaseSigning: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kawamonn.store"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kawamonn.store"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.5.0"

        buildConfigField("String", "STORE_API_BASE", "\"$storeApiBase\"")
        buildConfigField("String", "STORE_WEB_BASE", "\"$storeWebBase\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        localeFilters += listOf("ja", "en")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Googleログイン(Credential Manager 経由でIDトークンを取得し、Supabase Auth に渡す)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
