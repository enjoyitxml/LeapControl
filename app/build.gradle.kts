plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.leapcontrol.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.leapcontrol.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 204
        versionName = "0.2.4"
        resourceConfigurations += setOf("zh", "zh-rCN")
        buildConfigField("String", "AMAP_WEB_KEY_ENCRYPTED", "\"\"")
        buildConfigField("String", "PGYER_API_KEY_ENCRYPTED", "\"\"")
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // 背景视频必须原样打包：压缩过的资源拿不到文件描述符，MediaPlayer 会起播失败
    androidResources {
        noCompress += "mp4"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.md",
                "org/bouncycastle/**/*.properties"
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
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    debugImplementation("com.github.chuckerteam.chucker:library:4.1.0")
    releaseImplementation("com.github.chuckerteam.chucker:library-no-op:4.1.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    // 仅用于 JVM 单测：Android 内置的 org.json 在本地单测里不存在，需要显式提供 jar
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
