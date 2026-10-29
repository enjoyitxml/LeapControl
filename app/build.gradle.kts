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
        versionCode = 245
        versionName = "0.2.45"
        resourceConfigurations += setOf("zh", "zh-rCN")
        buildConfigField("String", "AMAP_WEB_KEY_ENCRYPTED", "\"\"")
        buildConfigField("String", "PGYER_API_KEY_ENCRYPTED", "\"\"")
        // 注意：这里不再注入任何 GitHub 令牌。仓库转为公开后，版本检测与附件下载
        // 全部走匿名请求，APK 里不留任何凭据（旧版本曾注入 GH_RELEASE_TOKEN）。
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    signingConfigs {
        // 发布包沿用本机 debug keystore 签名。
        //
        // 目的不是图省事，而是**保证老用户能直接覆盖安装**：0.2.44 及以前发出去的都是
        // debug 签名的包，如果 release 换一套 keystore，已装用户的签名不一致，
        // 系统会拒绝升级、必须先卸载（车友多半不会做这一步）。
        // 代价是这个 keystore 是公开可推断的，签名不具备防篡改性 —— 对自用分发可以接受；
        // 将来若要上架应用市场，再换成正式 keystore（届时也必须让用户卸载重装）。
        create("release") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // 不混淆：代码里有大量反射式 JSON 解析与 Kotlin 序列化，开 R8 风险大于收益
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            // release 变体下 chucker 走 library-no-op，调试面板不会被打进包里
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
