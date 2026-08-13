plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "com.ima.union"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        // ── 版本号:在根 gradle.properties 集中管理,支持 -PVERSION_NAME=... 覆盖 ──
        val versionName: String = (project.findProperty("VERSION_NAME") as String?) ?: "1.0.0-java"
        val versionCode: Int = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
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

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

// ============================================================================
// Maven 发布配置
// ----------------------------------------------------------------------------
// 制品坐标  : group:GROUP_ID  artifact:union-ad-sdk  version:VERSION_NAME
// 凭据来源  : gpr.user / gpr.key 走 project.findProperty()
//             → 由环境变量 GPR_USER / GPR_KEY 注入(参见 scripts/publish-github.sh)
//             → 或由 local.properties 的 gpr.user=... gpr.key=... 注入(本地联调)
// 默认仓库  : GitHub Packages (maven.pkg.github.com/<owner>/<repo>)
//             owner 由 -Pgithub.owner=<org> 传入,默认 ima-global
// 本地联调  : -Prepo=local,产物落入 ~/.m2/repository
// 覆盖版本  : -PVERSION_NAME=1.2.0-java -PVERSION_CODE=2
// ============================================================================

val groupId: String = (project.findProperty("GROUP_ID") as String?) ?: "com.ima.union"
val artifactId: String = "union-ad-sdk"
val projectVersion: String = (project.findProperty("VERSION_NAME") as String?) ?: "1.0.0-java"
val githubOwner: String = (project.findProperty("github.owner") as String?) ?: "ima-global"
val githubRepo: String = (project.findProperty("github.repo") as String?) ?: "FsUnionAd"

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = groupId
                artifactId = artifactId
                version = projectVersion

                // AGP Library:用 findByName 兼容不同 AGP 版本的 component 名(release / default)
                val releaseComponent = components.findByName("release")
                    ?: components.findByName("default")
                    ?: components.findByName("android")
                if (releaseComponent != null) {
                    from(releaseComponent)
                }

                pom {
                    name.set("UnionAd SDK")
                    description.set("聚合广告 SDK - 多平台广告统一接入、Bidding 竞价、瀑布流")
                    url.set("https://github.com/$githubOwner/$githubRepo")

                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }

                    developers {
                        developer {
                            id.set("ima-global")
                            name.set("IMA Global")
                            email.set("dev@ima.global")
                        }
                    }

                    scm {
                        url.set("https://github.com/$githubOwner/$githubRepo")
                        connection.set("scm:git:git://github.com/$githubOwner/$githubRepo.git")
                        developerConnection.set("scm:git:ssh://git@github.com/$githubOwner/$githubRepo.git")
                    }
                }
            }
        }

        repositories {
            // ── GitHub Packages(默认) ──
            maven {
                name = "githubPackages"
                url = uri("https://maven.pkg.github.com/$githubOwner/$githubRepo")
                credentials {
                    // 凭据从属性读,来源:环境变量(GPR_USER / GPR_KEY) → local.properties(本地)
                    val gprUser: String? = (project.findProperty("gpr.user") as String?)
                    val gprKey: String? = (project.findProperty("gpr.key") as String?)
                    if (!gprUser.isNullOrEmpty()) {
                        username = gprUser
                        password = gprKey ?: ""
                    }
                }
            }

            // ── 本地 Maven(~/.m2/repository)— -Prepo=local 启用 ──
            if ((project.findProperty("repo") as String?) == "local") {
                mavenLocal()
            }
        }
    }
}
