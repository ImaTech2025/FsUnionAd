plugins {
    id("com.android.library")
    id("maven-publish")
}

// ── 版本号 / Maven 坐标:在根 gradle.properties 集中管理,支持 -P 覆盖 ──
val versionName: String = (project.findProperty("VERSION_NAME") as String?) ?: "1.0.0-java"
val versionCode: Int = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
val mavenGroupId: String = (project.findProperty("GROUP_ID") as String?) ?: "com.ima.union"

android {
    namespace = "com.ima.union"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        buildConfigField("String", "SDK_VERSION", "\"$versionName\"")
        buildConfigField("int", "SDK_VERSION_CODE", "$versionCode")
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // ── 声明 release 变体参与 maven-publish,AGP 据此创建 SoftwareComponent ──
    publishing {
        singleVariant("release")
    }
}

dependencies {
    // ── 飞梭(Fission) SDK — aar 仅参与 UnionAd 编译期的类型解析,
    //    运行时由接入方按需引入并提供实际依赖(guava / lottie / appcompat 等)
    compileOnly(fileTree(mapOf("dir" to "libs/fission", "include" to listOf("*.aar"))))
    // ── 三方广告 SDK（compileOnly，由接入方按需引入） ──
    // 穿山甲 SDK
    compileOnly("com.pangle.cn:ads-sdk-pro:7.6.1.2")
    // 优量汇（腾讯广点通）SDK — aar 仅参与 UnionAd 编译期的类型解析
    compileOnly(fileTree(mapOf("dir" to "libs/gdt", "include" to listOf("*.aar"))))
    // 百青藤 SDK（百度联盟 MobAds）
    compileOnly("com.baidu:mobads:9.42.2")
}

// ── Maven 发布配置 ──
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = mavenGroupId
                artifactId = "FsUnionAd"
                version = versionName
            }
        }
    }
}

