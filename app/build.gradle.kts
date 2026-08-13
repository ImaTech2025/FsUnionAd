plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ima.fs.unionad"
    compileSdk = 36

    defaultConfig {
//        applicationId = "com.ima.fs.unionad"
        applicationId = "com.baidu.mobads.demo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // ── AndroidX 核心 ──────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ── Lifecycle (non-Compose) ────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")

    // ── Material Design 3 ──────────────────────────────────────
    implementation("com.google.android.material:material:1.12.0")

    // ── 聚合广告 SDK (Java 版) ────────────────────────────────
    implementation(project(":UnionAd"))

    // ── 三方广告 SDK（Demo 完整引入，供适配器反射桥接） ──────
    implementation("com.pangle.cn:ads-sdk-pro:7.6.1.2")  // 穿山甲
    implementation("com.baidu:mobads:9.42.2")              // 百青藤
    implementation(fileTree(mapOf("dir" to "libs/gdt", "include" to listOf("*.aar"))))

    // ── 飞梭(Fission) SDK 及其传递依赖（UnionAd 已 compileOnly,接入方按需引入） ──
    implementation(fileTree(mapOf("dir" to "libs/fission", "include" to listOf("*.aar"))))
    // appcompat 由 app 自身 1.7.0 提供,飞梭要求的 1.3.1 已覆盖
    implementation("com.google.guava:guava:31.0.1-android")
    implementation("com.airbnb.android:lottie:5.2.0")

    // ── 测试 ──────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
