import java.util.Properties

/** リリース署名。keystore.properties が無い環境ではデバッグ署名のままビルドが通る(yomumemo と同じ方式) */
val releaseSigning: Properties? = rootProject.file("pdf-toolkit/keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ttech.pdftoolkit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ttech.pdftoolkit"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file("pdf-toolkit/" + releaseSigning.getProperty("storeFile"))
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

    testOptions {
        // PdfBox-Android が内部で android.util.Log などを呼ぶため、JVMのユニットテストでは既定値を返すスタブにする
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
    }
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    // BouncyCastle は暗号化PDFの処理にしか使われず、既知の脆弱性がある古い版(1.72)が付いてくるため外す。
    // 暗号化(パスワード付き)PDFは読み込めない旨を画面に表示する。
    implementation(libs.pdfbox.android) {
        exclude(group = "org.bouncycastle")
    }

    testImplementation(libs.junit)
}
