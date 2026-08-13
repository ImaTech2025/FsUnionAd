# 聚合广告 SDK 整体架构设计文档

> 版本：1.0.0-java  
> 模块：`:UnionAd`  
> 语言：Java 11  
---

## 目录

1. [设计目标](#1-设计目标)
2. [整体分层架构](#2-整体分层架构)
3. [核心模块说明](#3-核心模块说明)
4. [数据模型设计](#4-数据模型设计)
5. [配置管理体系](#5-配置管理体系)
6. [广告策略引擎](#6-广告策略引擎)
7. [适配器体系](#7-适配器体系)
8. [广告格式管理器](#8-广告格式管理器)
9. [核心数据流](#9-核心数据流)
10. [设计模式总结](#10-设计模式总结)
11. [扩展性设计](#11-扩展性设计)
12. [线程模型](#12-线程模型)
13. [目录结构](#13-目录结构)

---

## 1. 设计目标

| 目标 | 说明 |
|---|---|
| **平台解耦** | 上层业务代码无需感知底层广告 SDK 差异，通过统一接口访问所有广告源 |
| **策略灵活** | 支持瀑布流、Bidding、混合三种竞价策略，可按广告位独立配置 |
| **配置驱动** | 策略类型、广告源等配置通过 JSON 管理，支持云端下发和热更新 |
| **配置选择** | 云端配置优先，无云端时用默认配置，实现灵活的配置管理 |
| **易于扩展** | 新增广告平台只需实现 `AdAdapter` 接口并注册，无需修改核心代码 |
| **纯 Java 实现** | 完全基于 Java 11，不依赖 Kotlin 运行时，兼容纯 Java 接入方 |
| **轻量低侵入** | SDK 本体不强制引入三方广告 SDK，采用 `compileOnly` 策略由接入方按需引入 |

---

## 2. 整体分层架构

```
┌──────────────────────────────────────────────────────────────┐
│                        接入方应用层                            │
│          Application.java / Activity / Fragment              │
└───────────────────────────┬──────────────────────────────────┘
                            │ 调用
┌───────────────────────────▼──────────────────────────────────┐
│                     SDK 统一入口层                             │
│                   FsUnionSDK.java                        │
│   initialize() / createXxxAdManager() / registerCustom()    │
└──────┬──────────┬──────────┬──────────┬──────────┬───────────┘
       │          │          │          │          │
  ┌────▼───┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐ ┌──▼─────┐
  │开屏广告 │ │插屏广告 │ │激励视频 │ │信息流  │ │信息流  │
  │Manager │ │Manager │ │Manager │ │模板   │ │自渲染  │
  │        │ │        │ │        │ │Manager│ │Manager│
  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └──┬─────┘
      └──────────┴──────────┴──────────┴─────────┘
                            │ load() 时传入 JSON
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                    策略管理层（config）                         │
│              CloudStrategyManager                              │
│  云端拉取 + 本地缓存 + defaultStrategyJson → 选择后的配置       │
└───────────────────────────┬──────────────────────────────────┘
                            │ 合并后的 AdUnitConfig
                            ▼
┌───────────────────────────▼──────────────────────────────────┐
│                     广告策略引擎层                              │
│                  AdStrategyManager.java                      │
│        ┌──────────────┬────────────────┐                     │
│   WaterfallStrategy  BiddingStrategy  HybridStrategy(priority chain)│
└──────────────┬────────────────────────────────────────────────┘
               │ 调用适配器
┌──────────────▼────────────────────────────────────────────────┐
│                     适配器注册层                                │
│                 AdAdapterRegistry.java                        │
│   ┌──────────┬──────────┬──────────┬──────────┬───────────┐  │
│   │ Pangle   │   GDT    │  Baidu   │ Fission  │  Custom   │  │
│   │ Adapter  │ Adapter  │ Adapter  │ Adapter  │ Adapter   │  │
│   └──────────┴──────────┴──────────┴──────────┴───────────┘  │
└───────────────────────────────────────────────────────────────┘
                            │ 调用
┌───────────────────────────▼──────────────────────────────────┐
│                   三方广告 SDK 层（compileOnly）                │
│    穿山甲 SDK / 优量汇 SDK / 百青藤 SDK / 飞梭 SDK             │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. 核心模块说明

### 3.1 模块包结构

```
com.ima.union/
├── FsUnionSDK.java                   # SDK 统一入口（根包）
├── utils/
│   ├── FsUnionSdkVersion.java       # SDK 版本号工具类
│   └── FsLogger.java               # 全局日志开关
├── core/
│   ├── model/                        # 数据模型（纯 POJO + Builder）
│   │   ├── AdFormat.java             # 广告格式枚举
│   │   ├── AdSdkType.java            # 广告平台类型（可扩展 final class）
│   │   ├── StrategyType.java         # 策略阶段类型枚举（WATERFALL / BIDDING）
│   │   ├── AdUnitConfig.java         # 广告位配置（合并后最终配置，含 strategies[] 数组）
│   │   ├── AdSourceConfig.java       # 广告源配置（合并后最终配置）
│   │   ├── StrategyItem.java         # 单个策略阶段配置（type/priority/timeoutMs/sources[]）
│   │   ├── AdRequestParams.java      # 广告请求参数（slotId + JSON）
│   │   ├── UnionAdResponse.java      # 统一广告响应（广告对象 + 竞价价格/有效期）
│   │   └── AdLoadResult.java         # 加载结果（Success/Failed）
│   ├── adapter/                      # 适配器接口体系
│   │   ├── AdAdapter.java            # 基础适配器接口
│   │   ├── SplashAdAdapter.java      # 开屏扩展接口
│   │   ├── InterstitialAdAdapter.java# 插屏扩展接口
│   │   ├── RewardedVideoAdAdapter.java
│   │   ├── FeedAdAdapter.java        # 信息流扩展接口
│   │   ├── AdAdapterRegistry.java    # 适配器注册表（单例）
│   │   ├── AdCallback.java           # 统一广告回调（onSuccess/onFailure）
│   │   ├── AdInitCallback.java       # SDK 初始化回调
│   │   ├── AdEventListener.java      # 广告事件基础接口
│   │   ├── SplashAdListener.java     # 开屏事件回调
│   │   ├── InterstitialAdListener.java
│   │   ├── RewardedVideoAdListener.java
│   │   └── FeedAdListener.java
│   ├── strategy/                     # 广告策略引擎
│   │   ├── AdStrategy.java           # 策略接口
│   │   ├── AdStrategyCallback.java   # 策略结果回调
│   │   ├── AdStrategyManager.java    # 策略管理器（单例, strategies.size()>=2 走 priority chain）
│   │   ├── BaseStrategy.java          # 策略基类（公共状态+方法）
│   │   ├── WaterfallStrategy.java    # 瀑布流策略
│   │   ├── BiddingStrategy.java      # 实时竞价策略
│   │   ├── HybridStrategy.java       # 优先级链策略（priority chain, 取代旧版 Hybrid）
│   │   └── StrategyUtils.java        # 策略工具类
│   └── config/                       # 配置管理层
│       ├── CloudConfig.java          # 云端/本地默认配置顶层模型
│       └── CloudStrategyManager.java   # 单例：HTTP 拉取 + SP 缓存 + 合并查询
├── manager/
│   ├── FsSplashAdManager.java        # 开屏广告管理器
│   ├── FsInterstitialAdManager.java # 插屏广告管理器
│   ├── FsRewardedVideoAdManager.java # 激励视频管理器
│   ├── FsFeedTemplateAdManager.java # 信息流模板管理器
│   └── FsFeedRenderAdManager.java  # 信息流自渲染管理器
├── adapters/
│   ├── pangle/PangleAdAdapter.java   # 穿山甲适配器
│   ├── gdt/GdtAdAdapter.java         # 优量汇适配器
│   ├── baidu/BaiduAdAdapter.java     # 百青藤适配器
│   ├── fission/FissionAdAdapter.java # 飞梭适配器
│   └── custom/BaseCustomAdAdapter.java # 自定义适配器基类
└── sample/
    └── SampleUsage.java              # 接入示例代码
```

---

## 4. 数据模型设计

> **设计原则**：模型类统一为可变 POJO + Builder 模式，同时承担 **"代码硬编码配置"** 和 **"JSON 解析模型"** 两种角色。所有字段可空，提供合理的默认值，因此不再需要 `CloudAdUnitConfig` / `CloudAdSourceConfig` / `ConfigMerger` 等中间层。

### 4.1 AdUnitConfig — 广告位配置

```
AdUnitConfig
├── strategyId      : String                 策略 ID（用于追踪请求所采用的策略来源），默认 "default"
├── slotId          : String                 广告位唯一标识（8 位数字）
├── adFormat        : AdFormat               广告格式（SPLASH / INTERSTITIAL / ...）
├── refreshInterval : long                   刷新间隔（ms，信息流使用）
└── strategies      : List<StrategyItem>     策略阶段数组（按 priority 升序串联成 priority chain）
                                              size==1: 单策略（BIDDING 或 WATERFALL）
                                              size>=2: 隐式混合（前一阶段 noFill 才推进到下一阶段）
```

构建方式：
- **代码硬编码**：`new AdUnitConfig.Builder().slotId("10000001").strategies(...).build()`
- **JSON 解析**：`AdUnitConfig.fromJson(jsonObject)`，识别顶层 `strategies[]` 数组，字段可空
- **运行时覆盖**：`AdRequestParams` 中传 `overrideJson` 时由 `CloudStrategyManager` 通过 `AdUnitConfig.fromJson` 反序列化
- **便捷方法**：`getSources()` / `getTimeoutMs()` / `getStrategyType()` 内部读取 `strategies[0]` 对应字段，保持子策略 `execute(AdUnitConfig)` 签名不变
- **混合判断**：`isHybrid()` 返回 `strategies.size() >= 2`

### 4.2 AdSourceConfig — 广告源配置

```
AdSourceConfig
├── sdkName      : String         当前广告源的全局别名（穿山甲/优量汇/飞梭/百青藤/自定义）
├── sdkType      : AdSdkType      对应广告平台
├── adFormat     : AdFormat       该源的广告格式
├── adUnitId     : String         平台侧广告位 ID
├── appId        : String         平台侧 App ID
├── token        : String         部分平台鉴权 token（如 Fission）
├── priority     : int            瀑布流排序优先级（数字越小越优先）
├── bidFloor     : double         Bidding 底价（单位：分），出价低于此值将被过滤
├── enabled      : boolean        是否启用
├── timeout      : long           单次请求超时（默认 5000ms）
├── extraParams  : Map<String,Object>  扩展参数
└── bidToken     : String         Bidding Token（运行时由适配器回写）
```

> **注意**：策略 ID（`strategyId`）只在 `AdUnitConfig`（unit）级别定义，`AdSourceConfig` 不再持有该字段。如需在源级别使用策略 ID，请通过所属 `AdUnitConfig.getStrategyId()` 获取。

`getSdkName()` 智能 fallback：优先返回配置中的 `sdkName` 字段值，为空时返回 `AdSdkType` 枚举的 `getSdkName()` 默认值（穿山甲 / 优量汇 / 百青藤 / 飞梭）。

### 4.3 枚举类型

```java
// 广告格式
enum AdFormat { SPLASH, INTERSTITIAL, REWARDED_VIDEO, FEED_TEMPLATE, FEED_RENDER }

// 广告平台
enum AdSdkType { PANGLE("穿山甲"), GDT("优量汇"), BAIDU("百青藤"), FISSION("飞梭"), CUSTOM("自定义") }

// 竞价策略阶段类型
// 混合策略不再以独立枚举项表达，由 AdUnitConfig.strategies.size()>=2 隐式承载
enum StrategyType { WATERFALL, BIDDING }
```

### 4.4 StrategyItem — 策略阶段配置

```
StrategyItem
├── type        : StrategyType     阶段策略类型（WATERFALL / BIDDING）
├── priority    : int              阶段执行优先级（数值越小越先执行，从 1 开始；相同时按 strategies 数组声明顺序）
├── timeoutMs   : long             该阶段最大执行超时（ms，默认 2000ms），仅控制当前阶段，不向下游透传
└── sources     : List<AdSourceConfig>  该阶段参与竞投/排序的广告源（与其它阶段完全独立）
```

构建方式：
- **代码硬编码**：`new StrategyItem.Builder().type(BIDDING).priority(1).timeoutMs(2000).sources(...).build()`
- **JSON 解析**：`StrategyItem.fromJson(jsonObject)` 字段可空，type 缺失时回退为 WATERFALL

**priority chain 语义**：
- `AdUnitConfig.strategies` 按 `priority` 升序串联成一条降级链
- 同一阶段内部由对应 type 的子策略自行调度 sources（`BiddingStrategy` 并发竞价 / `WaterfallStrategy` 串行瀑布流）
- 阶段内 source-level `priority`（即 `AdSourceConfig.priority`）保留，由 `WaterfallStrategy` 用于同阶段内瀑布流排序，与阶段间 `priority` 语义不混用

---

## 5. 配置管理体系

### 5.1 配置选择机制

SDK 在每次 `load()` 时自动选择配置，优先级从高到低：

```
┌───────────────────────────────────────────────────────────────┐
│  Layer 1: 本地缓存 / 云端配置                                    │
│  CloudStrategyManager 从 HTTP 服务端拉取并 SharedPreferences 缓存   │
│  支持版本号比较，自动更新                                        │
├───────────────────────────────────────────────────────────────┤
│  Layer 2: defaultStrategyJson（默认策略）                       │
│  load(defaultJson) 中传入的 JSON 或 assets 中预置的默认配置       │
│  优先级最低，作为兜底方案                                        │
└───────────────────────────────────────────────────────────────┘
```

### 5.2 JSON 解析模型（统一）

> **设计要点**：`AdUnitConfig` / `AdSourceConfig` 自身即可直接解析 JSON，无需中间转换层。
> `CloudStrategyManager` 内部直接调用 `AdUnitConfig.fromJson(jsonObject)` 即可拿到配置对象。

解析规则：
- **基本字段**：直接映射（slotId、adFormat、strategies[] 等）；顶层 strategyType 字段移除，由 `strategies.size() >= 2` 隐式判定 HYBRID
- **数值字段**：带默认值保护（timeoutMs 默认 3000ms，priority 默认 1，enabled 默认 true，timeout 默认 5000ms）
- **广告源列表**：将每个 source 节点用 `AdSourceConfig.fromJson()` 反序列化
- **adFormat 继承**：source 级别未配置 adFormat 时自动从 unit 级别继承
- **strategyId 默认值**：未配置时默认为 `"default"`，可通过此字段追踪请求所采用的策略来源。**该字段仅在 `AdUnitConfig`（unit）级别定义，`AdSourceConfig` 不再持有**。

```java
// 反序列化示例
AdUnitConfig unit = AdUnitConfig.fromJson(jsonObject);
String strategyId = unit.getStrategyId();   // "default" 或云端下发的策略 ID
```

### 5.3 CloudStrategyManager — 云端策略管理器

单例，职责：
1. **HTTP 拉取**：初始化时异步从服务端拉取最新配置
2. **本地缓存**：使用 `SharedPreferences` 持久化缓存配置
3. **版本号比较**：比较本地缓存版本与云端版本，有更新时替换
4. **策略选择**：对外暴露 `getMergedUnitConfig()` 方法，按优先级选择策略配置

```java
public class CloudStrategyManager {
    // 按 slotId 查询并选择配置：云端 > 默认
    AdUnitConfig getMergedUnitConfig(String slotId, String defaultStrategyJson);
}
```

### 5.4 配置加载时序

```
Application.onCreate()
    │
    ▼
FsUnionSDK.initialize()
    │
    ├── 注册各平台适配器
    ├── 初始化 CloudStrategyManager
    │       ├── 从 SharedPreferences 恢复本地缓存
    │       └── 异步 HTTP 拉取云端配置
    │
    ▼
Activity.onCreate()
    │
    ▼
FsUnionSDK.createXxxAdManager(context)  // 仅创建，不加载配置
    │
    ▼
manager.load(AdRequestParams)  // 触发配置选择 + 广告加载
    │
    ├── CloudStrategyManager.getMergedUnitConfig(slotId, defaultStrategyJson)
    │       ├── 读取本地缓存/云端配置（cloud）→ AdUnitConfig（CloudConfig.fromJson）
    │       ├── 若 cloud 存在 → 直接返回（AdUnitConfig 自身即可 JSON 解析）
    │       ├── 若 cloud 不存在 → 解析 defaultStrategyJson 为 AdUnitConfig → 返回
    │       └── 两者皆无 → 返回 null
    │
    ▼
AdStrategyManager.execute(context, mergedUnitConfig, callback)
```

---

## 6. 广告策略引擎

### 6.1 策略接口

```java
public interface AdStrategy {
    StrategyType getStrategyType();    // HybridStrategy 返回 null（不对应单一枚举项）
    void execute(Context context, AdUnitConfig unitConfig, AdStrategyCallback callback);
    void cancel();
}

public interface AdStrategyCallback {
    void onAdLoaded(UnionAdResponse response);
    void onNoFill(String reason);
}
```

### 6.2 瀑布流策略（WaterfallStrategy）

**机制：** 按优先级（`priority`）升序、eCPM 降序排列广告源，逐源串行请求，首次成功即返回。

```
广告源列表（按 priority 升序、ecpm 降序排序）
      │
      ▼
┌─────────────┐   成功  ┌──────────────┐
│  请求 Source 1 │ ──────► │ onAdLoaded() │
└─────────────┘         └──────────────┘
      │ 失败/超时
      ▼
┌─────────────┐   成功  ┌──────────────┐
│  请求 Source 2 │ ──────► │ onAdLoaded() │
└─────────────┘         └──────────────┘
      │ 失败/超时
      ▼
   ... 继续
      │ 全部失败
      ▼
  onNoFill()
```

- 单线程 `ExecutorService` 串行执行；`requestWithTimeout` 为**纯转发**（包装 AdCallback + 调 `adapter.request`），不阻塞当前线程
- 单源微观超时由各 Adapter 内部 `AdLoadTimeout` 兜底（PangleAdAdapter / FissionAdAdapter），或透传给平台 SDK（BaiduAdAdapter 用 `SplashAd.KEY_TIMEOUT`），超时后以 `onFailure(-200, ...)` 形式回到 WaterfallStrategy.onLoadResult 推进下一源
- 整体链路宏观超时由 `scheduledExecutor` 在 `execute` 入口调度，到点置 `timeoutFlipped` 触发 `onNoFill`；`onLoadResult` 头部也判 `timeoutFlipped` 丢弃"在途响应"
- 支持 `cancel()` 中断当前执行链

### 6.3 实时竞价策略（BiddingStrategy）

**机制：** 向所有启用广告源并发发起 Bid 请求，等待统一超时时间，取最高出价者加载广告。

```
所有广告源（并发）
 ┌──────┬──────┬──────┐
 │Bid A │Bid B │Bid C │  ← 并发请求，CachedThreadPool
 └──┬───┴──┬───┴──┬───┘
    │      │      │
    ▼      ▼      ▼
 1000分  800分 1200分   ← bidFloor 过滤后
    └──────┴──────┘
           │ 等待 timeoutMs
           ▼
       选最高出价者 C（1200分）
           │
           ▼
       加载 C 的广告
           │
    成功 ──┤── 失败
    │              │
onAdLoaded()  onNoFill()
```

- `CachedThreadPool` 并发请求，`CountDownLatch` 等待全部完成或超时
- `bidFloor` 过滤：出价低于底价的 Bid 不参与最终竞争
- 使用 `synchronized (bidResponses)` 保证竞价结果列表线程安全

### 6.4 优先级链策略（HybridStrategy / Priority Chain）

**机制**：遍历 `AdUnitConfig.strategies`，按 `priority` 升序串联成一条降级链。
前一阶段返回 `noFill` 才推进到下一阶段；前阶段成功（`onAdLoaded`）则整条链终止。
同一阶段内由对应 `type` 的子策略（`BiddingStrategy` 并发竞价 / `WaterfallStrategy` 串行瀑布流）自行调度 sources。
**混合策略不再对应独立枚举项**，由 `strategies.size() >= 2` 隐式表达。

```
AdUnitConfig.strategies (按 priority 升序)
      │
      ├─► Stage 1: priority=1 type=BIDDING   ──► BiddingStrategy(subSources, timeoutMs=stage1)
      │                                                  │ noFill
      ├─► Stage 2: priority=2 type=WATERFALL ──► WaterfallStrategy(subSources, timeoutMs=stage2)
      │                                                  │ noFill
      ├─► Stage 3: priority=3 ...             ──► ...
      │                                                  │ noFill
      └─► 全部 noFill ────────────────────────► onNoFill("All strategies noFill")
```

**关键设计点**：
- **阶段独立超时**：每个 `StrategyItem.timeoutMs` 仅控制当前阶段，不向下游透传；不再有"整段链路 timeoutMs"调度
- **阶段间 sources 完全独立**：同一 SDK 可在不同阶段配置不同 adUnitId（甚至同一 adUnitId 复用以实现"先竞价后瀑布兜底"）
- **构造单阶段虚拟 unitConfig**：给子策略调用前用 `AdUnitConfig.Builder.fromCopy(unitConfig).strategies(wrapSingleStage(stage))` 构造只含当前阶段的 unitConfig；子策略通过 `getSources()` / `getTimeoutMs()` 便捷方法自动读到本阶段数据
- **cancel 转发**：`activeSubStrategy` 持有当前活跃子策略实例，外部 `cancel()` 时转发 cancel 给子策略
- **回调驱动式链推进**：阶段间通过 `onNoFill` 触发下一阶段；阶段间无共享线程池，无锁竞争

**与旧版 HybridStrategy（已删除）的对比**：
- 旧版按 `AdAdapter.supportBidding()` 拆 bidding/waterfall 两组 sources；新版每阶段 sources 由 JSON 显式声明
- 旧版有 `AdUnitConfig.biddingTimeoutMs` / `waterfallTimeoutMs` / `timeoutMs` 三段超时；新版每阶段一个 `timeoutMs`
- 旧版硬编码"Bidding → Waterfall fallback"两段；新版支持任意阶段数、任意类型组合（如 WATERFALL → BIDDING → WATERFALL）

### 6.5 BaseStrategy — 策略基类

3 个策略（WaterfallStrategy / BiddingStrategy / HybridStrategy）共用的状态字段、调度器、回调投递方法和 cancel 通用逻辑抽取到 `BaseStrategy` 抽象类：
- 4 个状态标志位 `cancelled` / `cancelFlipped` / `timeoutFlipped` / `adLoadedFlipped`
- `scheduledExecutor` 统一从 `ExecutorManager.getScheduled(TIMEOUT_SCHEDULE)` 拿
- `notifyAdLoaded` / `notifyNoFill` 统一在主线程回调(Looper 短路)
- `resetFlags()` execute() 入口重置 4 个标志位
- `cancel()` 通用模板: 置 3 个标志位 + 调用 `onCancel()` 模板方法, 子类可重写做额外清理

### 6.6 AdStrategyManager — 策略管理器

单例，根据 `AdUnitConfig.strategies()` 工厂化对应策略并启动执行：

```java
public void execute(Context context, AdRequestParams params, AdUnitConfig unitConfig, AdStrategyCallback callback) {
    AdStrategy strategy;
    List<StrategyItem> stages = unitConfig.getStrategies();
    if (stages == null || stages.isEmpty()) {
        strategy = new WaterfallStrategy();   // 空配置, 立即 noFill
    } else if (stages.size() >= 2) {
        strategy = new HybridStrategy();      // priority chain
    } else {
        // 单阶段: 根据 type 路由
        strategy = stages.get(0).getType() == StrategyType.BIDDING
                ? new BiddingStrategy()
                : new WaterfallStrategy();
    }
    strategy.execute(context, params, unitConfig, callback);
}
```

不持有任何"进行中槽位"或并发控制；每次 `execute()` 发起一次全新任务，多次同 slotId 的请求完全独立、互不影响。

---

## 7. 适配器体系

### 7.1 AdAdapter — 基础适配器接口

```java
public interface AdAdapter {
    AdSdkType getSdkType();           // 返回平台枚举
    String getAdapterVersion();       // 适配器版本（"pangle_1.0.0"）
    boolean isInitialized();   // 初始化状态（统一委托到 AdAdapterRegistry.isInited() ，唯一真相源）

    void initialize(Context, String appId, AdInitCallback);  // 初始化 SDK
    void request(Context, AdSourceConfig, AdCallback);       // 统一广告请求（竞价 + 加载）
    default boolean supportBidding() { return false; }       // 是否支持 Bidding
    void destroy();                                          // 释放资源
}
```

### 7.2 格式扩展接口

各广告格式通过扩展接口暴露展示方法：

```java
SplashAdAdapter.showSplash(context, response, listener)
InterstitialAdAdapter.showInterstitial(context, response, listener)
RewardedVideoAdAdapter.showRewardedVideo(context, response, listener)
FeedAdAdapter.bindFeedView(context, response, container, listener)
```

### 7.3 AdAdapterRegistry — 适配器注册表

单例，采用 `ConcurrentHashMap` 按注册顺序维护适配器：

```
AdAdapterRegistry (单例)
├── adapters: Map<AdSdkType, AdAdapter>             内置平台适配器
├── customAdapters: Map<String, AdAdapter>          自定义适配器（key=adapter.getSdkName()）
├── initializedApps: Set<String>                    已初始化记录（唯一真相源，key="SDK类型_AppId"；Adapter.isInitialized() 统一委托到此）
└── pendingInits: Map<String, List<AdInitListener>> 正在初始化的 key → 等待回调的监听器列表
```

#### 7.3.1 初始化机制（同步 + 异步双版本）

`AdAdapterRegistry` 提供两套初始化入口，对外统一通过 **`AdInitListener`** 回调通知结果，外部根据成功/失败自行决定下一步操作：

| 入口 | 行为 | 适用场景 |
|---|---|---|
| `ensureInitialized(...)` | **同步阻塞**版本，底层委托给 `ensureInitializedAsync` + `CountDownLatch.await`（最多 `INIT_TIMEOUT_MS=10s`） | 业务方在应用启动阶段"先初始化完再继续" |
| `ensureInitializedAsync(..., listener)` | **纯异步**版本，初始化完成（或失败/超时）后通过 `AdInitListener` 主动通知 | 业务方希望"非阻塞、知道结果"地接入初始化 |

**核心契约**：
- 同一 `{SDK+AppId}` 组合只会触发一次真正的平台 SDK 初始化（去重通过 `initializedApps` + `pendingInits`）
- 多个并发调用方等待同一 key 时，所有 listener 都会被通知（共享 `pendingInits[key]` 列表）
- 兜底：若平台 SDK 长时间不触发 `AdInitCallback`（超过 `INIT_TIMEOUT_MS=10s`），`ScheduledExecutorService` 会以 `errorCode=0` 回调 `onInitFailure`，避免调用方永久阻塞
- **幂等保证**：每个 listener 必然被精确通知一次 — 通过 `pendingInits.remove(key)` 的返回值做去重（首次 remove 返回 waiters 列表，二次走任何回调路径时 remove 返回 null → 跳过通知）
- 失败时**不**写入 `initializedApps`，业务方可选择重试或降级

**`AdInitListener` 接口签名**：

```java
public interface AdInitListener {
    void onInitSuccess(AdAdapter adapter);
    /** errorCode: 0=等待超时, -1=被中断, 其它=平台 SDK errorCode */
    void onInitFailure(AdAdapter adapter, int errorCode, String errorMsg);
}
```

**回调线程**：与平台 SDK `AdInitCallback` 触发线程一致（通常在 SDK 内部 init 线程）。如需在主线程处理，请使用 `ExecutorManager.postToMain(runnable)` 切换。

#### 7.3.2 业务方使用示例

```java
// 1. 启动阶段同步初始化（阻塞到完成）
AdAdapterRegistry.getInstance().ensureInitialized(
    context, adapter, appId, token);

// 2. 广告请求前异步初始化（拿到结果后继续/降级）
AdAdapterRegistry.getInstance().ensureInitializedAsync(
    context, adapter, appId, token,
    new AdInitListener() {
        @Override public void onInitSuccess(AdAdapter a) {
            // 继续走 bid/loadAd
        }
        @Override public void onInitFailure(AdAdapter a, int code, String msg) {
            // 跳过当前广告源 / 重试 / 降级到其他源
        }
    });
```

#### 7.3.3 策略层接入方式

`BiddingStrategy` / `WaterfallStrategy` 内部均使用 `ensureInitializedAsync`：
- 在策略子线程内发起异步初始化
- 通过 `initLatch` 把异步转同步（保持策略原有的串行/并行控制流）
- 失败时跳过当前广告源，继续尝试下一个（瀑布流）或忽略该 bidder（竞价）

### 7.4 已内置适配器

| 适配器类 | 平台 | Bidding 支持 | 开屏 | 插屏 | 激励视频 | 信息流模板 | 信息流自渲染 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `PangleAdAdapter` | 穿山甲（字节）| ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GdtAdAdapter` | 优量汇（腾讯）| ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `BaiduAdAdapter` | 百青藤（百度）| ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `FissionAdAdapter` | 飞梭 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `BaseCustomAdAdapter` | 自定义 | 可覆写 | — | — | — | — | — |

### 7.5 BaseCustomAdAdapter — 自定义适配器基类

提供 `CUSTOM` SDK 类型绑定和默认 Bidding 拒绝实现，开发者只需实现 `loadAd()`、`initialize()` 等核心方法。

### 7.6 core/ad 包目录结构（业务方 / SDK 内部 / 物料 Key 三层职责）

`com.ima.union.core.ad` 按职责拆分为三个子包，业务方只看 iface + listener 即可，impl 隐藏在 SDK 内部：

```
core/ad/
├── AdExtraKeys.java                     # 物料字段 key 全局静态常量（被 3 层引用，保持顶层）
├── iface/                               # 对外数据接口 — 业务方主要接触这一层
│   ├── IFsUnionAd.java                  # 顶层统一接口（getEcpm/getSdkType/getSdkName/getSlotId）
│   ├── IFsUnionSplashAd.java            # 开屏接口
│   ├── IFsUnionInterstitialAd.java      # 插屏接口
│   ├── IFsUnionRewardedVideoAd.java     # 激励视频接口
│   ├── IFsUnionNativeAd.java            # 信息流自渲染接口（提供素材方法 + reportShow/Click）
│   └── IFsUnionNativeExpressAd.java     # 信息流模板接口
├── listener/                            # 展示回调接口 — 业务方 new 后注册到广告对象
│   ├── FsUnionSplashAdListener.java
│   ├── FsUnionInterstitialAdListener.java
│   ├── FsUnionRewardedVideoAdListener.java
│   ├── FsUnionNativeAdListener.java
│   └── FsUnionNativeExpressAdListener.java
└── impl/                                # SDK 内部实现 — 业务方不应直接引用
    ├── FsUnionSplashAd.java             # implements IFsUnionSplashAd
    ├── FsUnionInterstitialAd.java
    ├── FsUnionRewardedVideoAd.java
    ├── FsUnionNativeAd.java
    └── FsUnionNativeExpressAd.java
```

**业务方最小知识集**：
- 加载回调收到 `IFsUnion*Ad`（iface）→ 调用 `show()` / `getExpressView()` / 素材方法
- 展示事件通过 `FsUnion*AdListener`（listener）回调
- 物料字段 key 统一从 `AdExtraKeys` 引用
- **永远不需要直接引用 `core.ad.impl.*` 下的任何类**——那是 SDK 内部细节，构造器虽然 public 但 javadoc 标记仅供各格式 Manager 内部使用

---

## 8. 广告格式管理器

所有格式管理器采用统一的模式：

```
XxxAdManager
├── context        : Context
├── lastSlotId     : String              最后一次加载的广告位 ID
├── mergedUnitConfig: AdUnitConfig       合并后的最终配置（懒加载）
├── loadedAd       : IFsUnionXxxAd       加载完成的广告对象接口（封装 UnionAdResponse + 适配器）
├── isLoading      : boolean             防止重复请求
└── listener       : FsUnionXxxAdListener 事件回调（通过广告对象传回）

方法：
├── XxxAdManager(Context context)                    构造函数（极简，仅需 Context）
├── setListener(FsUnionXxxAdListener) : XxxAdManager 链式设置监听器
├── load(AdRequestParams params)                      触发策略引擎加载广告
├── getLoadedAd() : IFsUnionXxxAd                     获取已加载的广告对象接口
├── isReady() : boolean                               是否已有可展示的广告
└── destroy()                                         释放资源，取消策略
```

**广告对象接口（IFsUnionXxxAd）**

加载成功后，管理器会创建对应格式的广告对象（内部实现为 `FsUnionXxxAd`），通过 **接口** 暴露给业务方，**业务方只面向接口编程**，不接触实现细节：

| 格式 | 广告对象接口 | 展示方法 | 信息获取 |
|---|---|---|---|
| 开屏 | `IFsUnionSplashAd` | `show(ViewGroup)` | `getEcpm()` / `getSdkType()` / `getSdkName()` / `getSlotId()` |
| 插屏 | `IFsUnionInterstitialAd` | `show()` | `getEcpm()` / `getSdkType()` / `getSdkName()` / `getSlotId()` |
| 激励视频 | `IFsUnionRewardedVideoAd` | `show()` | `getEcpm()` / `getSdkType()` / `getSdkName()` / `getSlotId()` |
| 信息流模板 | `IFsUnionNativeExpressAd` | `getExpressView()` | `getEcpm()` / `getSdkType()` / `getSdkName()` / `getSlotId()` |
| 信息流自渲染 | `IFsUnionNativeAd` | 素材方法 + `reportShow/Click()` | `getTitle()` / `getDescription()` / `getIconUrl()` ... |

**核心设计：**
- **构造函数极简化**：仅需 `Context`，广告位信息通过 `AdRequestParams` 传入
- **配置从 JSON 驱动**：`strategies[]` 数组中各阶段的 `type` 字段、`sources` 等从 JSON 配置中解析
- **配置选择内置**：`load()` 内部自动调用 `CloudStrategyManager.getMergedUnitConfig(slotId, ...)` 选择配置
- **slotId 严格校验**：`AdStrategyManager.execute()` 入口处强制校验 `params.slotId == unitConfig.slotId`，不一致时 `onNoFill` 直接回调，避免用错位配置拉取广告
- **广告对象封装展示**：加载成功后返回封装好的广告对象，外部通过对象调用 `show()` 或获取素材
- **信息透明**：广告对象暴露 `getEcpm()`、`getSdkType()`、`getSdkName()` 等属性，便于业务层做价格分析和来源追踪
- **接口与实现分离**：业务方只接触 `IFsUnion*Ad` 接口，内部实现类 `FsUnion*Ad` 隐藏在 `core/ad` 包，构造器仅供 SDK 内部 Manager 使用

**物料 Key 全局常量（AdExtraKeys）**

为统一物料字段命名，所有 `UnionAdResponse.extra` 中的字段 key 都集中在 `com.ima.union.core.ad.AdExtraKeys`：

| 常量 | 值 | 用途 |
|---|---|---|
| `TITLE` | `"title"` | 广告标题 |
| `DESCRIPTION` | `"description"` | 广告描述 |
| `ICON_URL` | `"icon_url"` | 图标 URL |
| `IMAGE_URL` | `"image_url"` | 单张大图 URL |
| `IMAGE_LIST` | `"image_list"` | 多图 URL 列表 |
| `VIDEO_URL` | `"video_url"` | 视频 URL |
| `CTA` | `"cta"` | 行动文案（如"立即下载"） |
| `RATING` | `"rating"` | 星级评分 |

平台 Adapter 在写入 `extra` Map 时、媒体在 `IFsUnionNativeAd.getExtra()` 中读取自定义字段时，均应引用 `AdExtraKeys` 常量，避免散落的硬编码字符串。

**AdStrategyManager** 是所有格式管理器的下游，负责选择和执行具体策略。

---

## 9. 核心数据流

### 9.1 完整广告加载流程（新设计）

```
接入方调用 manager.load(AdRequestParams)
    │
    ▼
CloudStrategyManager.getMergedUnitConfig(slotId, defaultStrategyJson)
    │
    ├── 读取本地缓存/云端配置 → AdUnitConfig（cloud，CloudConfig.fromJson）
    │   ├── 若 cloud 存在
    │   │   └── 直接返回
    │   └── 若 cloud 不存在
    │       └── 解析 defaultStrategyJson → CloudConfig → AdUnitConfig（默认兜底）
    │
    ▼
返回 AdUnitConfig（选择后的最终配置）
    │
    ▼
AdStrategyManager.execute(context, mergedUnitConfig, callback)
    │ 创建对应 Strategy 实例
    ▼
Strategy.execute()
    │
    ├── [Waterfall] 按排序串行遍历 sources
    │       └── AdAdapter.request() → AdCallback
    │
    ├── [Bidding] 并发向所有 sources 发起 request()
    │       └── AdCallback → 选最高价者
    │
    └── [Hybrid]  先 Bidding sources，失败降级 Waterfall
    │
    ▼
AdStrategyCallback.onAdLoaded(UnionAdResponse)
    │
    ▼
XxxAdManager 创建 FsUnionXxxAd(UnionAdResponse, adapter)  [内部实现]
    并通过接口 IFsUnionXxxAd 暴露给业务层
    │
    ▼
接入方获取 IFsUnionXxxAd 接口对象，调用 ad.show() 或 ad.getExpressView()
    │
    ▼
FsUnionXxxAd 内部委托 AdAdapter.showXxx(context, response, listener)
    │
    ▼
底层广告 SDK 渲染展示广告
```

### 9.2 Bidding 竞价流程

```
Strategy 并发 request() → 各平台 SDK 计算 eCPM → UnionAdResponse(price, adUnitId, nativeAd)
    │
    ├── price < bidFloor  → 丢弃
    └── price >= bidFloor → 加入竞价列表
         │
         ▼
timeoutMs 超时后（或全部完成）
         │
         ▼
  responses.max(price) → 竞标胜者
         │
         ▼
  winner.findSourceConfig(adUnitId) → 唯一定位 AdSourceConfig
         │
         ▼
  winner → UnionAdResponse（携带 adUnitId）
```

> **adUnitId 链路唯一性说明**：一家广告 SDK 可在策略中配置多个 adUnitId 参与竞价，仅靠 sdkName 无法唯一定位 AdSourceConfig。
> `UnionAdResponse.adUnitId` 是竞价链路的唯一确定性标识，由 `BiddingStrategy.findSourceConfig` 用于反查配置。

### 9.3 云端配置更新流程

```
Application.onCreate() → FsUnionSDK.initialize()
    │
    ▼
CloudStrategyManager 初始化
    │
    ├── 1. 从 SharedPreferences 读取本地缓存配置
    │
    └── 2. 异步 HTTP 请求云端配置
            │
            ├── 成功：
            │   ├── 比较版本号（云端版本 > 本地版本）
            │   ├── 更新本地缓存
            │   └── 后续 load() 自动使用新配置
            │
            └── 失败：
                └── 继续使用本地缓存配置
```

---

## 10. 设计模式总结

| 模式 | 应用位置 | 说明 |
|---|---|---|
| **Builder** | `Config`、`AdUnitConfig`、`AdSourceConfig`、`StrategyItem`、`UnionAdResponse` | 避免长构造函数，支持链式调用 |
| **Strategy** | `WaterfallStrategy`、`BiddingStrategy`、`HybridStrategy`（priority chain） | 竞价策略可替换，符合开闭原则 |
| **Registry（注册表）** | `AdAdapterRegistry` | 平台适配器注册与查找，解耦调用方与实现方 |
| **Factory Method** | `FsUnionSDK.createXxxAdManager()` | 封装格式管理器的创建，校验初始化状态 |
| **Singleton** | `AdAdapterRegistry`、`AdStrategyManager`、`CloudStrategyManager` | 全局唯一，线程安全 |
| **Template Method** | `BaseCustomAdAdapter` | 规定自定义适配器骨架，子类填充细节 |
| **Observer（回调）** | `AdCallback`、`XxxAdListener` | Java 接口回调替代 Kotlin Flow |
| **Facade** | `FsUnionSDK` | 对外暴露简洁统一的入口，隐藏内部复杂性 |
| **配置驱动** | JSON → CloudConfig → AdUnitConfig（统一模型） | 策略和广告源通过配置管理，支持热更新 |

---

## 11. 扩展性设计

### 11.1 新增广告平台

只需三步，零修改核心代码：

```java
// 1. 继承 BaseCustomAdAdapter，通过 AdSdkType.of() 注册自定义类型
public class MyAdAdapter extends BaseCustomAdAdapter {
    private static final AdSdkType AD_SDK_TYPE = AdSdkType.of("MY_ADAPTER", "我的适配器");
    @Override public AdSdkType getSdkType() { return AD_SDK_TYPE; }
    @Override public boolean supportBidding() { return true; }
    // ... 实现其他方法
}

// 2. 在 Application.onCreate() 注册（需指定广告格式）
FsUnionSDK.registerCustomAdapter(new MyAdAdapter(), AdFormat.SPLASH);
FsUnionSDK.registerCustomAdapter(new MyAdAdapter(), AdFormat.REWARDED_VIDEO);

// 3. 在 JSON 配置中使用（schema: strategy 下放单条 AdUnitConfig，slotId 内部字段）
{
    "strategy": {
        "slotId": "10000006",
        "strategyId": "default",
        "strategies": [
            {
                "type": "BIDDING",
                "priority": 1,
                "timeoutMs": 2500,
                "sources": [
                    {
                        "sdkName": "自定义平台",
                        "sdkType": "MY_ADAPTER",
                        "adUnitId": "my_slot_id",
                        "appid": "my_app_id",
                        "priority": 1,
                        "bidFloor": 7.0
                    }
                ]
            }
        ]
    }
}
```

### 11.2 新增广告格式

1. 在 `AdFormat` 枚举中新增值
2. 继承现有 `AdAdapter` 接口新增格式方法
3. 新建 `XxxAdManager` 实现加载/展示逻辑（构造函数只需 `Context`）
4. 在 `FsUnionSDK` 中新增 `createXxxAdManager(context)` 工厂方法

### 11.3 新增竞价策略

**单策略**：实现 `AdStrategy` 接口，在 `AdStrategyManager.execute()` 中针对新 `StrategyType` 增加分支。
**混合 priority chain**：无需新增 strategy 类，只在 JSON `strategies[]` 数组中追加更多 `StrategyItem` 即可（如 `WATERFALL → BIDDING → WATERFALL` 三段）。
如需自定义链调度逻辑，可继承 `HybridStrategy` 覆盖阶段推进方法。

### 11.4 云端配置扩展

如需扩展云端配置的字段：
1. 在 `AdUnitConfig` / `AdSourceConfig` 中新增字段（包装类型以支持 JSON 缺失）
2. 在 `AdUnitConfig.fromJson()` / `AdSourceConfig.fromJson()` 中添加解析逻辑
3. 在 `AdUnitConfig.Builder` / `AdSourceConfig.Builder` 中暴露新字段的 setter
4. 同步更新 `getXxx()` 方法的默认值（getter 内置 null-safe 默认值）

---

## 12. 线程模型

```
调用方（主线程）
    │
    ▼
XxxAdManager.load()          → 主线程调用
    │
    ▼
CloudStrategyManager.getMergedUnitConfig() → 主线程（本地缓存读取无 IO）
    │
    ▼
AdStrategyManager.execute()  → 主线程调用
    │
    ▼
WaterfallStrategy            → SingleThreadExecutor（后台串行）
BiddingStrategy               → CachedThreadPool（后台并发）
HybridStrategy                → 委托给上面两者
    │
    ▼ （通过 Handler(Looper.getMainLooper())）
AdStrategyCallback / XxxAdListener  → 主线程回调
    │
    ▼
XxxAdManager.show()          → 主线程调用（必须）
    │
    ▼
AdAdapter.showXxx()          → 主线程执行（广告展示 UI 操作）
```

**原则：**
- 所有 SDK 初始化、广告加载网络请求在后台线程执行
- 所有回调统一切换到主线程（`Handler.post()`）
- 广告展示 UI 操作始终在主线程
- 云端配置 HTTP 拉取在后台线程，不影响主线程广告请求

---

## 13. 目录结构

```
UnionAd/
├── build.gradle.kts                # 模块构建配置
├── libs/
│   └── fission/
│       ├── Fission-1.0.99.06-open.aar  # 飞梭 SDK AAR
│       └── 飞梭SDK接入文档.pdf
├── src/
│   └── main/
│       └── java/
│           └── com/ima/union/
│               ├── FsUnionSDK.java         # SDK 入口（根包）
│               ├── utils/           # 工具类
│               ├── core/           # 核心层（模型、策略、适配器接口、配置管理）
│               ├── manager/        # 广告格式管理器
│               ├── adapters/       # 平台适配器实现
│               └── sample/         # 接入示例
├── 聚合SDK架构文档.md      # 本文档
├── 接入文档.md
└── README.md
```

---

*文档由 AI 辅助生成，基于实际代码反向整理，与代码保持同步。*
