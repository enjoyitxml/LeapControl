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
        versionCode = 252
        versionName = "0.2.52"
        resourceConfigurations += setOf("zh", "zh-rCN")
        buildConfigField("String", "AMAP_WEB_KEY_ENCRYPTED", "\"\"")
        buildConfigField("String", "PGYER_API_KEY_ENCRYPTED", "\"\"")
        // 注意：这里不再注入任何 GitHub 令牌。仓库转为公开后，版本检测与附件下载
        // 全部走匿名请求，APK 里不留任何凭据（旧版本曾注入 GH_RELEASE_TOKEN）。
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

/**
 * 让 debug 产物直接带上版本号：`app-debug.apk` → `LeapControl-0.2.46-debug.apk`。
 *
 * 为什么不用 `archivesBaseName`：AGP 8 里它对 APK 不再生效（只影响 AAR/JAR），
 * 改输出文件名的正规入口是 variant output 的 `outputFileName`。
 * 版本号从 defaultConfig 读，保证与 `versionName` 永远一致，不需要在这儿再抄一份。
 */
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val vName = android.defaultConfig.versionName ?: "0"
        variant.outputs.forEach { output ->
            // outputFileName 不在公共的 VariantOutput 接口上，只有 AGP 内部实现类
            // VariantOutputImpl 才暴露它，所以这里必须下转一次（AGP 8.x 的通行做法）。
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName
                ?.set("LeapControl-$vName-debug.apk")
        }
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
