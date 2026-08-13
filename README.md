# FsUnionAd — 聚合广告 SDK（Java 版）

> 一套代码，接入多平台广告，智能竞价，最大化广告收益。

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![Language](https://img.shields.io/badge/language-Java%2011-orange.svg)](https://openjdk.org/projects/jdk/11/)
[![Version](https://img.shields.io/badge/version-1.0.0--java-informational.svg)](CHANGELOG.md)
[![](https://jitpack.io/v/ImaTech2025/FsUnionAd.svg)](https://jitpack.io/#ImaTech2025/FsUnionAd)
---

## 简介

**FsUnionAd FsUnionSDK（Java 版）** 是一款面向 Android 应用开发者的开源聚合广告 SDK，纯 Java 实现，无需 Kotlin 运行时依赖。通过统一的接口，一次接入即可管理穿山甲、优量汇、百青藤、飞梭等主流广告平台，并内置瀑布流、实时竞价（Bidding）、混合三种竞价策略，帮助开发者在保障广告填充率的同时最大化 eCPM 收益。

---

## 功能特性

### 🏗️ 统一接口，一次接入
无需了解各广告平台 SDK 的差异，通过 `FsUnionSDK` 统一 API 完成所有广告格式的加载和展示。

### 💰 三种竞价策略
| 策略 | 说明 | 适用场景 |
|---|---|---|
| **瀑布流（Waterfall）** | 按优先级和 eCPM 顺序逐一请求，首次成功返回 | 填充率优先 |
| **实时竞价（Bidding）** | 并发向所有平台发起竞价，取最高出价者展示 | 收益最大化 |
| **混合（Hybrid）** | 支持 Bidding 的平台竞价，其余瀑布流兜底 | 推荐默认使用 |

> 📝 **策略配置方式**：策略类型（`StrategyType`）通过 JSON 策略配置文件指定，支持云端下发和本地默认配置，无需在代码中硬编码。

### 📺 全广告格式支持

| 格式 | 说明 |
|---|---|
| **开屏广告** | App 启动时全屏展示，支持倒计时跳过 |
| **插屏广告** | 关键节点弹窗展示，支持全屏/半屏 |
| **激励视频** | 完整观看获得奖励，验证回调安全可靠 |
| **信息流模板** | 平台自渲染模板，快速接入 |
| **信息流自渲染** | 自定义布局渲染，高度定制化 |

### 🔌 已接入广告平台

| 平台 | 适配器 | Bidding | 开屏 | 插屏 | 激励视频 | 信息流 |
|---|---|:---:|:---:|:---:|:---:|:---:|
| **穿山甲**（字节跳动）| `PangleAdAdapter` | ✅ | ✅ | ✅ | ✅ | ✅ |
| **优量汇**（腾讯广点通）| `GdtAdAdapter` | ✅ | ✅ | ✅ | ✅ | ✅ |
| **百青藤**（百度）| `BaiduAdAdapter` | ✅ | ✅ | ✅ | ✅ | ✅ |
| **飞梭**（Fission）| `FissionAdAdapter` | ✅ | ✅ | ✅ | ✅ | ✅ |

### 🧩 高度可扩展
通过实现 `BaseCustomAdAdapter` 接口，可在不修改 SDK 源码的情况下接入任意自定义广告平台。

### ☕ 纯 Java 实现
完全基于 Java 11，不引入 Kotlin 运行时，对纯 Java 项目友好，兼容性更广泛。

---

## 快速开始

### 1. 添加依赖

**Step 1.** 在根目录 `settings.gradle.kts` 中添加 JitPack 仓库：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**Step 2.** 在 app 模块的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation("com.github.ImaTech2025:FsUnionAd:v1.0.1")
    // 按需引入对应平台 SDK
    implementation("com.pangle.cn:ads-sdk-pro:7.6.1.2")
    implementation("com.qq.e.union:union:4.560.1470")
}
```

> 完整接入步骤详见 [接入文档](接入文档.md)。

### 2. 初始化 SDK

```java
// Application.onCreate()
FsUnionSDK.initialize(
    this,
    new FsUnionSDK.Config.Builder()
        .appId("your_app_id")
        .enableLog(BuildConfig.DEBUG)
        .build(),
    success -> Log.i("App", "初始化" + (success ? "成功" : "失败"))
);
```

### 3. 加载并展示广告

```java
// 以激励视频为例 —— 使用静态 loadAd 方法
// 1. 调用静态方法加载广告，传入加载回调
FsRewardedVideoAdManager.loadAd(context,
        new AdRequestParams.Builder()
                .slotId("10000003")
                .defaultStrategyJson(loadJsonFromAssets("ad_strategy_rewarded.json"))
                .build(),
        new FsRewardedVideoAdManager.OnRewardedVideoAdLoadListener() {
            @Override
            public void onAdLoaded(IFsUnionRewardedVideoAd ad) {
                // 加载成功，保存广告对象并设置展示回调
                Log.d("Rewarded", "Ad ready: sdkName=" + ad.getSdkName())
                        + ", ecpm=" + ad.getEcpm());
                ad.setListener(new FsUnionRewardedVideoAdListener() {
                    @Override
                    public void onAdShow(IFsUnionRewardedVideoAd ad) {
                        Log.d("Rewarded", "Ad shown");
                    }
                    @Override
                    public void onRewardVerify(IFsUnionRewardedVideoAd ad, boolean verify, int amount, String name) {
                        if (verify) grantReward(amount, name);
                    }
                    @Override
                    public void onAdClose(IFsUnionRewardedVideoAd ad) {
                        // 预加载下一条
                        onCreate(context);
                    }
                    @Override public void onAdClick(IFsUnionRewardedVideoAd ad) {}
                    @Override public void onAdError(IFsUnionRewardedVideoAd ad, int code, String msg) {}
                });
            }

            @Override
            public void onAdLoadError(int errorCode, String errorMsg) {
                Log.e("Rewarded", "Load failed [" + errorCode + "]: " + errorMsg);
            }
        });

// 2. 在按钮点击时通过广告对象展示
if (rewardedAd != null) rewardedAd.show();
```

> **API 设计说明**：加载回调（`OnXxxAdLoadListener`）与展示回调（`FsUnionXxxAdListener`）分离：
> - `OnXxxAdLoadListener`：加载成功返回广告对象，失败返回错误码和错误信息
> - `FsUnionXxxAdListener`：广告展示后的交互回调（曝光、点击、关闭等）
> - 广告对象（`IFsUnionXxxAd`）：封装展示能力和广告信息查询，**业务方只面向接口编程**，隐藏内部实现类（`FsUnionXxxAd`）

| 格式 | 静态加载方法 | 广告对象接口 | 展示方式 |
|---|---|---|---|
| 开屏 | `FsSplashAdManager.loadAd(...)` | `IFsUnionSplashAd` | `ad.show(ViewGroup)` |
| 插屏 | `FsInterstitialAdManager.loadAd(...)` | `IFsUnionInterstitialAd` | `ad.show()` |
| 激励视频 | `FsRewardedVideoAdManager.loadAd(...)` | `IFsUnionRewardedVideoAd` | `ad.show()` |
| 信息流模板 | `FsFeedTemplateAdManager.loadAd(...)` | `IFsUnionNativeExpressAd` | `ad.getExpressView()` |
| 信息流自渲染 | `FsFeedRenderAdManager.loadAd(...)` | `IFsUnionNativeAd` | 素材方法 + `ad.reportShow()` |

> 📖 **策略配置**：策略类型、广告源列表等配置通过 JSON 文件管理，支持 **云端下发 > 本地缓存 > 默认 JSON** 三级合并。详见 [接入文档.md](接入文档.md) 的「广告策略配置」章节。

---

## 项目结构

```
UnionAd/
├── FsUnionSDK.java                   # SDK 统一入口（根包）
├── utils/
│   ├── FsUnionSdkVersion.java       # SDK 版本号工具类
│   └── FsLogger.java               # 全局日志开关
├── core/
│   ├── model/                        # 数据模型（POJO + Builder）
│   ├── adapter/                      # 适配器接口与回调
│   ├── strategy/                     # 竞价策略引擎
│   └── config/                      # 云端/本地配置管理
├── manager/
│   ├── FsSplashAdManager.java        # 开屏广告管理器
│   ├── FsInterstitialAdManager.java # 插屏广告管理器
│   ├── FsRewardedVideoAdManager.java# 激励视频管理器
│   ├── FsFeedTemplateAdManager.java # 信息流模板管理器
│   └── FsFeedRenderAdManager.java  # 信息流自渲染管理器
├── adapters/
│   ├── pangle/                       # 穿山甲适配器
│   ├── gdt/                          # 优量汇适配器
│   ├── baidu/                        # 百青藤适配器
│   ├── fission/                      # 飞梭适配器
│   └── custom/                       # 自定义适配器基类
├── sample/
│   └── SampleUsage.java              # 完整接入示例
└── README.md                         # 本文档
```

---

## 核心 API

### FsUnionSDK（SDK 入口）

```java
// 初始化
FsUnionSDK.initialize(context, config, callback)

// 注册自定义平台
FsUnionSDK.registerCustomAdapter(adapter)

// 状态查询
FsUnionSDK.isInitialized()

// 释放资源
FsUnionSDK.destroy()
```

### 广告加载（静态方法）

所有广告格式通过对应 Manager 的静态 `loadAd()` 方法加载，无需创建 Manager 实例：

```java
// 开屏广告
FsSplashAdManager.loadAd(context, params, new FsSplashAdManager.OnSplashAdLoadListener() {
    @Override public void onAdLoaded(IFsUnionSplashAd ad) { /* 加载成功 */ }
    @Override public void onAdLoadError(int errorCode, String errorMsg) { /* 加载失败 */ }
});

// 插屏广告
FsInterstitialAdManager.loadAd(context, params, new FsInterstitialAdManager.OnInterstitialAdLoadListener() { ... });

// 激励视频
FsRewardedVideoAdManager.loadAd(context, params, new FsRewardedVideoAdManager.OnRewardedVideoAdLoadListener() { ... });

// 信息流模板
FsFeedTemplateAdManager.loadAd(context, params, new FsFeedTemplateAdManager.OnNativeExpressAdLoadListener() { ... });

// 信息流自渲染
FsFeedRenderAdManager.loadAd(context, params, new FsFeedRenderAdManager.OnNativeAdLoadListener() { ... });
```

### 广告对象与展示回调

加载成功后通过 `OnXxxAdLoadListener.onAdLoaded(ad)` 回调获取广告对象，再调用 `ad.setListener()` 设置展示回调：

| 格式 | 广告对象接口 | 展示方法 | 展示回调接口 |
|---|---|---|---|
| 开屏 | `IFsUnionSplashAd` | `show(ViewGroup)` | `FsUnionSplashAdListener` |
| 插屏 | `IFsUnionInterstitialAd` | `show()` | `FsUnionInterstitialAdListener` |
| 激励视频 | `IFsUnionRewardedVideoAd` | `show()` | `FsUnionRewardedVideoAdListener` |
| 信息流模板 | `IFsUnionNativeExpressAd` | `getExpressView()` | `FsUnionNativeExpressAdListener` |
| 信息流自渲染 | `IFsUnionNativeAd` | 素材方法 + `reportShow()` | `FsUnionNativeAdListener` |

### AdRequestParams（广告请求参数）

```java
new AdRequestParams.Builder()
        .slotId("10000001")                     // 广告位 ID（必填，8 位数字）
        .defaultStrategyJson(defaultJson)       // 默认策略 JSON（可选）
        .build()
```

### 配置选择优先级

SDK 在每次 `load()` 时自动选择配置，优先级从高到低：

```
本地缓存/云端配置 > defaultStrategyJson（load 时传入）
```

---

## 架构设计

本 SDK 采用分层架构，从上到下分为：**入口层 → 格式管理层 → 策略引擎层 → 适配器注册层 → 三方 SDK 层**。

核心设计原则：
- **Strategy 模式**：竞价策略（瀑布流/Bidding/混合）可灵活替换
- **Registry 模式**：适配器通过注册表统一管理，支持运行时动态扩展
- **Facade 模式**：`FsUnionSDK` 作为统一门面，隐藏内部复杂性
- **Builder 模式**：所有配置对象使用 Builder 构造，避免多参数构造函数
- **配置驱动**：策略类型、广告源等配置通过 JSON 管理，支持云端下发和热更新

详细架构说明请查看 [聚合SDK整体架构设计文档.md](聚合SDK架构文档.md)

---

## JitPack 集成

> SDK 通过 [JitPack](https://jitpack.io/#ImaTech2025/FsUnionAd) 发布，无需私有仓库凭证。

### 1. 添加 JitPack 仓库

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // 各广告平台 Maven 仓库
        maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
        maven { url = uri("https://dl-maven-android.tencent.com/repository/maven") }
    }
}
```

### 2. 引入 SDK 依赖

```kotlin
dependencies {
    implementation("com.github.ImaTech2025:FsUnionAd:v1.0.1")

    // 按需引入各广告平台 SDK（必须，否则该平台无实际能力）
    implementation("com.pangle.cn:ads-sdk-pro:7.6.1.2")
    implementation("com.baidu:mobads:9.42.2")
    // ...
}
```

> 版本号对应 GitHub Release Tag，可在 [JitPack](https://jitpack.io/#ImaTech2025/FsUnionAd) 查看可用版本。

### 3. 运行时获取 SDK 版本号

```java
String version = FsUnionSDK.getVersion();          // "v1.0.1"
int code = FsUnionSDK.getVersionCode();            // 1
```

### 4. 维护者发布新版本

```bash
# 提交版本号变更
git add gradle.properties
git commit -m "chore: bump version to 1.1.0-java"

# 打 tag（必须以 v 开头）
git tag v1.1.0-java
git push origin main
git push origin v1.1.0-java

# JitPack 自动检测 tag 并构建制品
# GitHub Actions 同步发布到 GitHub Packages 并生成 Release Notes
```

推送 tag 后，JitPack 会自动检测并构建。访问 `https://jitpack.io/#ImaTech2025/FsUnionAd` 确认构建状态。

---

## 环境要求

| 项目 | 要求 |
|---|---|
| Android minSdk | 21（Android 5.0）|
| compileSdk | 34 |
| Java | 11 |
| AGP | 7.0+ |

---

## 相关文档

- 📐 [架构设计文档](聚合SDK架构文档.md) — 分层设计、策略引擎、数据流说明
- 📖 [接入文档](接入文档.md) — 完整的接入步骤和代码示例
- 📊 [埋点文档](聚合SDK埋点文档.md) — 事件定义、ext 参数、上报规范
- 🔄 [逻辑流程图](聚合SDK逻辑流程图.md) — 全链路时序图与状态流转
- 💻 [SampleUsage.java](UnionAd/src/main/java/com/ima/union/sample/SampleUsage.java) — 可运行的完整示例代码

---

## License

```
Copyright 2026 FsUnionAd Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
