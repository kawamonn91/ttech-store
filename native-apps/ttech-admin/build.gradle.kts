import java.util.Properties

/**
 * リリース署名。keystore.properties が無い環境ではデバッグ署名のままビルドが通る。
 *
 * 重要: このアプリは管理者だけがストア経由でダウンロードする。署名鍵を変えると更新できなくなるため、
 * この鍵は失くさず・変えないこと(バックアップは Google ドライブ G:\マイドライブ\app\ttech-admin\)。
 */
val releaseSigning: Properties? = rootProject.file("ttech-admin/keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

/** 接続先。local.properties で上書きできる(エミュレータからローカルの検証サーバーに繋ぐ場合など) */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
/** 上書きの優先順: ./gradlew -PadminWebBase=... > local.properties > 本番 */
fun localOrProject(name: String): String? = (findProperty(name) as String?) ?: localProperties.getProperty(name)

val adminWebBase: String = localOrProject("adminWebBase") ?: "https://store.kawamonn.com"
val supabaseUrl: String = localOrProject("supabaseUrl") ?: "https://bniehgjuylkgxbstmjdh.supabase.co"

/** anon キーは「公開してよい」設計の値(ストアアプリ・Web版と同じ。データはRLSで保護される) */
val supabaseAnonKey: String = localOrProject("supabaseAnonKey")
    ?: "sb_publishable_JmdR3qk3rytSemnuaeOI_g_Z9SLuiaQ"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ttech.admin"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ttech.admin"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

        buildConfigField("String", "ADMIN_WEB_BASE", "\"$adminWebBase\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file("ttech-admin/" + releaseSigning.getProperty("storeFile"))
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

    androidResources {
        localeFilters += listOf("ja", "en")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
