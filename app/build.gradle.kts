plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "work.temp1209.kakeibo"
    compileSdk = 36

    defaultConfig {
        applicationId = "work.temp1209.kakeibo"
        minSdk = 26
        targetSdk = 36
        // CI配布ビルドはこの値を上書きし、Firebase App Distribution上でビルドを区別できるようにする。
        versionCode = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // ローカル(Android Studio)とCI(GitHub Actions)で同じdebug鍵を使うために、
            // AGPが自動生成する ~/.android/debug.keystore ではなく固定のキーストアを明示的に指定する。
            // CIランナーはビルドのたびに使い捨てのため、これを指定しないと毎回異なる鍵で署名され、
            // Firebase App Distributionでの更新が「別アプリ」扱いとなりデータが全消去されてしまう。
            // 固定鍵は.gitignoreで除外され、Firebase配布CI(distribute.yml)がGitHub Secretsから
            // 復元する。それ以外のCI(android.yml、Firebase配布とは無関係)ではファイルが無いため、
            // AGP既定の自動生成debug署名にフォールバックする(このCIはただの検証用でFirebaseへ
            // 配布しないため、署名の一貫性は不要)。
            val pinnedDebugKeystore = file("debug.keystore")
            if (pinnedDebugKeystore.exists()) {
                storeFile = pinnedDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}