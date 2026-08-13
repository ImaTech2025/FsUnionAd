# 聚合 SDK 全链路埋点设计文档

> **版本**: v1.6 | **日期**: 2026-08-11 | **模块**: `com.ima.union`

---

## 目录

1. [设计规则](#1-设计规则)
2. [埋点事件](#2-埋点事件)
3. [基础 ext 参数](#3-基础-ext-参数)
4. [事件链路总览](#4-事件链路总览)
5. [链路拆分与事件详情](#5-链路拆分与事件详情)
6. [埋点接入指南](#6-埋点接入指南)

---

## 1. 设计规则

### 1.1 事件标识

```java
/**
 * 埋点事件上报统一接口。
 *
 * @param eventId 事件名称（见第 2 节埋点事件汇总）
 * @param ext    扩展参数 Map，包含请求级、源级、结果级信息
 */
public interface FsAdTracker {
    void trackEvent(String eventId, Map<String, Object> ext);
}
```

| 规则 | 说明 |
|------|------|
| **eventId** | `String` 类型，`fs_<action>` / `fs_<action>_suc` / `fs_<action>_fail` 格式 |
| **ext** | `Map<String, Object>` 类型，Key 全小写驼峰，Value 为 String/Number/Boolean |
| **唯一性** | 同一次广告请求通过 `requestId` 贯穿全链路 |
| **线程安全** | 上报方法可在任意线程调用，实现方需保证线程安全 |
| **空值处理** | ext 中 Value 为 null 时不放入 Map（避免序列化异常） |

### 1.2 命名规范

| 维度 | 规范 | 示例 |
|------|------|------|
| eventId | `fs_<action>` 启动 / `fs_<action>_suc` 成功 / `fs_<action>_fail` 失败 | `fs_request`、`fs_bid_suc`、`fs_strategy_request_fail` |
| ext Key | 全小写驼峰 | `requestId`、`slotId`、`sdkType` |
| ext Value 类型 | String 优先，数值用 Long/Double，布尔用 Boolean | `"PANGLE"`, `3000L`, `3.5` |

### 1.3 核心事件链路

全链路埋点围绕五个核心事件展开，覆盖从请求到点击的完整生命周期：

```
fs_request  ──►  fs_request_suc  ──►  fs_bid  ──►  fs_show  ──►  fs_click
  (请求)          (召回/请求成功)       (竞价)       (曝光)         (点击)
```

在此核心链路之间，穿插云端策略请求（4 事件）、策略调度（3 事件）以及展示等辅助事件，所有请求级/竞价级/展示级事件均到单广告源粒度（通过 `sdkType`/`adUnitId`/`requestId` ext 区分），详见 [第 2 节](#2-埋点事件)。

---

## 2. 埋点事件

以下为全链路 18 个埋点事件汇总，按生命周期阶段分组：

### 2.1 云端策略请求阶段

> 对应 `CloudStrategyManager.getMergedUnitConfig()` 全流程：内存缓存 → SP 缓存 → 异步云端拉取 → default JSON 兜底 → 合并输出。

| eventId | 含义 | 上报时机 |
|---------|------|----------|
| `fs_strategy_request` | 触发云端策略请求 | 内存缓存 + SP 缓存均未命中，且配置了 cloudUrl，触发异步 HTTP 请求时 |
| `fs_strategy_request_suc` | 云端策略请求成功 | HTTP 200 + 响应 JSON 有效 + slotId 校验通过 + 版本号 > 本地缓存时 |
| `fs_strategy_request_fail` | 云端策略请求失败 | HTTP 非 200 / 网络异常 / slotId 不匹配 / 版本号未增长（任意失败路径） |
| `fs_strategy_merge_suc` | 策略配置合并完成 | 最终 `AdUnitConfig` 就绪时（来源：云端缓存 / default JSON / 首次云端拉取成功更新后的新配置） |

**`fs_strategy_request`** / **`fs_strategy_request_suc`** / **`fs_strategy_request_fail`**（配置合并前，无策略信息）：
- 基础 ext 参数：`requestId`, `slotId`, `cTime`
- 特有 ext 参数：`fetchUrl`, `connectTimeoutMs`, `readTimeoutMs`, `httpCode`, `cloudVersion`, `bodySize`, `fetchCostMs`, `isVersionUpdated`, `failReason`, `errorMsg`

**`fs_strategy_merge_suc`**（配置合并完成，策略信息就绪）：
- 基础 ext 参数：`requestId`, `slotId`, `strategyId`, `strategyType`, `cTime`
- 特有 ext 参数：`configSource`, `sourceCount`, `stageCount`, `mergeCostMs`

### 2.2 策略调度阶段

> 对应 `AdStrategyManager.execute()` 全流程：slotId 校验 → 策略执行 → 回调结果。`fs_strategy_exec` 通过 ext `strategyType` 区分策略类型（`BIDDING`/`WATERFALL`/`HYBRID`），不再为各策略单独设入口事件。

| eventId | 含义 | 上报时机 |
|---------|------|----------|
| `fs_strategy_exec` | 策略执行开始 | 具体策略（`BiddingStrategy`/`WaterfallStrategy`/`HybridStrategy`）`execute()` 调用前（ext `strategyType` 标识类型） |
| `fs_strategy_exec_suc` | 策略执行成功 | 策略层 `notifyAdLoaded()` → 回调 `onAdLoaded()` 时 |
| `fs_strategy_exec_fail` | 策略执行失败 | 策略层 `notifyNoFill()` → 回调 `onNoFill()` 时 / slotId 校验失败 |

**`fs_strategy_exec`**（入口第一层，尚未确定具体广告源）：
- 基础 ext 参数：`requestId`, `slotId`, `adFormat`, `strategyId`, `strategyType`, `cTime`
- 特有 ext 参数：`strategyImpl`, `chainTimeoutMs`, `firstStageType`, `firstStageSourceCount`, `firstStageTimeoutMs`

**`fs_strategy_exec_suc`**（策略成功，持有最终胜出广告源信息）：
- 基础 ext 参数：`requestId`, `slotId`, `adFormat`, `strategyId`, `strategyType`, `sdkType`, `sdkName`, `adUnitId`, `bidFloor`, `cTime`
- 特有 ext 参数：`winnerEcpm`, `bidRank`, `totalBids`, `fromCache`, `execCostMs`

**`fs_strategy_exec_fail`**（策略失败，未召回任何广告）：
- 基础 ext 参数：`requestId`, `slotId`, `adFormat`, `strategyId`, `strategyType`, `cTime`
- 特有 ext 参数：`failReason`, `execCostMs`, `failedSourceCount`, `bidResponseCount`

### 2.3 请求级事件（单广告源粒度）

> **全部到单广告源粒度**：每个广告源（由 `sdkType` + `adUnitId` 唯一定位）独立上报 `fs_request`/`_suc`/`_fail`。ext 中 `sdkType`/`sdkName`/`adUnitId`/`bidFloor` 必有值。
>
> **去重规则**：总请求数 = `COUNT(DISTINCT requestId)`；单广告源指标（填充率/失败率）= `COUNT(DISTINCT requestId) WHERE sdkType=X AND adUnitId=Y`。

| eventId | 含义 | 上报时机 | 策略类型 |
|---------|------|----------|----------|
| `fs_request` | 单广告源请求发起 | 每个广告源发起加载请求时（含 Waterfall 串行源、Bidding 并行源） | ALL |
| `fs_request_suc` | 单广告源加载成功 | 单个广告源 `onLoadResult(Success)` 回调时（无论是否为最终胜出源） | ALL |
| `fs_request_fail` | 单广告源加载失败 | 单个广告源 `onLoadResult(Failure)` / 初始化失败 / 无适配器 / 超时被跳过 时 | ALL |

**基础 ext 参数**：`requestId`, `slotId`, `adFormat`, `strategyId`, `strategyType`, `sdkType`, `sdkName`, `adUnitId`, `bidFloor`, `cTime`

**特有 ext 参数**：`sourceIndex`, `totalSources`, `sourceTimeoutMs`, `ecpm`, `isWinner`, `fromCache`, `sourceCostMs`, `failReason`, `errorCode`, `errorMsg`, `isLastSource`

### 2.4 竞价级事件（Bidding 专属，单广告源粒度）

> Bidding 策略下每个广告源在 `fs_request` 之外额外触发竞价事件。ext 中 `sdkType`/`sdkName`/`adUnitId`/`bidFloor` 必有值。

| eventId | 含义 | 上报时机 | 策略类型 |
|---------|------|----------|----------|
| `fs_bid` | 开始竞价 | `performParallelBidding()` 启动并行竞价时 | BIDDING |
| `fs_bid_suc` | 单源竞价成功 | 广告源通过底价过滤且 isReady=true，加入 `bidResponses` 时 | BIDDING |
| `fs_bid_fail` | 单源竞价失败 | 广告源加载失败 / 被过滤（超时/未就绪/低于底价）时 | BIDDING |

**`fs_bid`**（竞价入口，per-source 循环中触发，AdSourceConfig 可获取）：
- 基础 ext 参数：`requestId`, `slotId`, `adFormat`, `strategyId`, `strategyType`, `sdkType`, `sdkName`, `adUnitId`, `bidFloor`, `cTime`
- 特有 ext 参数：`bidderCount`, `chainTimeoutMs`, `hasCacheBid`

**`fs_bid_suc`** / **`fs_bid_fail`**（同上，单广告源粒度）：
- 基础 ext 参数：`requestId`, `slotId`, `adFormat`, `strategyId`, `strategyType`, `sdkType`, `sdkName`, `adUnitId`, `bidFloor`, `cTime`
- 特有 ext 参数：`ecpm`, `bidRank`, `fromCache`, `isReady`, `bidFailReason`, `errorCode`, `errorMsg`

### 2.5 展示级事件（单广告源粒度）

| eventId | 含义 | 上报时机 | 策略类型 |
|---------|------|----------|----------|
| `fs_show` | 广告曝光 | 业务方调用展示方法，广告实际渲染到屏幕时 | ALL |
| `fs_click` | 广告点击 | 三方 SDK 点击回调触发时 | ALL |
| `fs_close` | 广告关闭 | 广告被关闭/跳过时 | ALL |
| `fs_video_complete` | 视频播放完成 | 激励视频播放完成时 | REWARDED_VIDEO |
| `fs_reward` | 激励发奖 | 激励视频完成播放且符合发奖条件时 | REWARDED_VIDEO |

**基础 ext 参数**：`requestId`, `slotId`, `adFormat`, `strategyId`, `strategyType`, `sdkType`, `sdkName`, `adUnitId`, `cTime`

**特有 ext 参数**：`ecpm`, `fromCache`, `showCostMs`, `isReady`, `clickToImpressionGap`, `closeAction`, `displayDurationMs`, `videoDurationMs`, `watchedDurationMs`, `rewardVerified`, `rewardAmount`, `rewardName`

> **变更说明**（v1.5→v1.6）：**收口到两层事件**——删除 Waterfall/Chain 专属事件（`fs_waterfall_exec`/`_source_suc`/`_source_fail` + `fs_chain_exec`/`_stage_suc`/`_stage_fail` 共 6 个），策略层统一使用 `fs_strategy_exec`（通过 `strategyType` ext 区分 BIDDING/WATERFALL/HYBRID）。`fs_request`/`_suc`/`_fail` 下沉到单广告源粒度（每个广告源独立上报，ext 含 `sdkType`/`adUnitId`/`requestId`/`strategyType`）。展示级事件同步到单源粒度。总计 24→**18** 事件。

> **变更说明**（v1.4→v1.5）：重构 Waterfall/Chain 事件命名，对齐 Bidding 的 `_exec`/`_suc`/`_fail` 模式——`fs_waterfall`→`fs_waterfall_exec`，`fs_waterfall_source` 拆为 `_source_suc`/`_source_fail`；`fs_chain`→`fs_chain_exec`，`fs_chain_result` 拆为 `_stage_suc`/`_stage_fail`。总计 22→24 事件。

> **变更说明**（v1.3→v1.4）：`CloudConfigManager` 重命名为 `CloudStrategyManager`；配置阶段事件统一重命名：`fs_config_fetch`→`fs_strategy_request`、`fs_config_fetch_suc`→`fs_strategy_request_suc`、`fs_config_fetch_fail`→`fs_strategy_request_fail`、`fs_config_merge_suc`→`fs_strategy_merge_suc`。

---

## 3. 基础 ext 参数

以下参数在**所有事件**中均需携带（如该阶段有值），构成全链路追踪的基线：

| Key | 类型 | 说明 | 来源 |
|-----|------|------|------|
| `requestId` | String | 单次广告请求唯一标识，全链路贯穿 | 请求入口客户端生成（MD5） |
| `slotId` | String | 聚合广告位 ID（8 位数字） | `AdRequestParams.getSlotId()` |
| `adFormat` | String | 广告格式（`SPLASH`/`INTERSTITIAL`/`REWARDED_VIDEO`/`FEED_TEMPLATE`/`FEED_RENDER`） | `AdUnitConfig.getAdFormat()` |
| `strategyId` | String | 策略 ID，标识配置来源（默认 `"default"`） | `AdUnitConfig.getStrategyId()` |
| `strategyType` | String | 策略类型（`BIDDING`/`WATERFALL`/`HYBRID`） | `AdUnitConfig.getStrategyType()` |
| `sdkType` | String | 广告平台类型（`PANGLE`/`GDT`/`BAIDU`/`FISSION`/自定义） | `AdSourceConfig.getSdkType().getSdkName()` |
| `sdkName` | String | 广告源名称（可能与 sdkType 不同，支持多 adUnitId） | `AdSourceConfig.getSdkName()` |
| `adUnitId` | String | 平台侧广告位 ID | `AdSourceConfig.getAdUnitId()` |
| `bidFloor` | Double | 底价（单位：分） | `AdSourceConfig.getBidFloor()` |
| `cTime` | Long | 事件发生时间，Unix 毫秒时间戳 | 上报时 `System.currentTimeMillis()` |

> **注意**：策略级/配置级事件中，`fs_strategy_request`/`_suc`/`_fail`、`fs_strategy_merge_suc`、`fs_strategy_exec`、`fs_strategy_exec_fail` 不涉及具体广告源，`sdkType`/`sdkName`/`adUnitId`/`bidFloor` 为空；`fs_strategy_exec_suc` 持有最终胜出广告源，上述字段必有值。源级事件（`fs_request`/`_suc`/`_fail`、`fs_bid`/`_suc`/`_fail`、`fs_show`/`fs_click`/`fs_close`/`fs_video_complete`/`fs_reward`）中这些字段必有值。

---

## 4. 事件链路总览

```
业务方调用 loadAd
       │
       ▼
 ═══════════════════ StrategyRequest 云端策略请求 ═══════════
       │
       ├─ 缓存命中 ──────────────────────────┐
       │                                     │
       ├─ 缓存未命中（有 cloudUrl）            │
       │   └─► fs_strategy_request            │
       │         ├─ fs_strategy_request_suc    │
       │         └─ fs_strategy_request_fail   │
       │                                     │
       └──► fs_strategy_merge_suc ◄───────────┘
              （最终 AdUnitConfig 就绪）
       │
       ├─ 合并失败 → fs_strategy_exec_fail
       │
       ▼
 ═══════════════════ Strategy 策略调度 ═══════════════════
       │
       ├─ slotId 不匹配 → fs_strategy_exec_fail
       │
       ▼
┌─ fs_strategy_exec (ext.strategyType = BIDDING/WATERFALL/HYBRID) ────┐
│  策略执行开始，覆盖所有策略类型                                     │
└────────────────────────────────────────────────────────────────────┘
       │
       ▼
┌─ 各广告源独立上报（per source） ──────────────────────────────────┐
│                                                                    │
│  ┌─ fs_request ──────────────────────────────────────────────┐    │
│  │  单源请求发起（ext: sdkType/adUnitId/requestId/strategyType）│   │
│  └───────────────────────────────────────────────────────────┘    │
│       │                                                            │
│       ├── Bidding 源 ──► fs_bid ──► fs_bid_suc / fs_bid_fail     │
│       │                                                            │
│       └──► fs_request_suc / fs_request_fail （所有源统一出口）    │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
       │
       ├── 任一个源成功 → fs_strategy_exec_suc
       │
       ├── 全部源失败   → fs_strategy_exec_fail
       │
       ▼
┌─ 展示级事件（per source，ext 含 sdkType/adUnitId/requestId） ─────┐
│                                                                    │
│  fs_show ──► fs_click                                              │
│           ──► fs_close                                             │
│           ──► fs_video_complete ──► fs_reward（仅激励视频）        │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## 5. 链路拆分与事件详情

### 5.1 云端策略请求事件（Strategy Request）

#### 5.1.1 `fs_strategy_request` — 触发云端策略请求

**触发时机**：`ensureSlotConfigLoaded()` 中，内存缓存 + SP 缓存均未命中，且 `configUrl` 已配置，触发 `fetchFromCloud()` 异步 HTTP 请求时。

> **注意**：如果已有缓存命中（内存或 SP），则不会触发此事件。此事件只在实际发起 HTTP 请求时上报。

**基础 ext**：`requestId`、`slotId`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `fetchUrl` | String | 请求 URL（脱敏，仅保留域名路径） |
| `connectTimeoutMs` | Long | 连接超时配置（ms） |
| `readTimeoutMs` | Long | 读取超时配置（ms） |

---

#### 5.1.2 `fs_strategy_request_suc` — 云端策略请求成功

**触发时机**：`fetchFromCloud()` 异步线程中，HTTP 200 且响应 JSON 解析有效、slotId 校验通过、版本号 > 本地缓存版本时。

> **注意**：版本号 <= 本地时不会触发此事件（属于 `fs_strategy_request_fail`）。

**基础 ext**：`requestId`、`slotId`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `httpCode` | Integer | HTTP 响应状态码（200） |
| `cloudVersion` | Long | 云端返回的配置版本号 |
| `bodySize` | Integer | 响应体大小（字节） |
| `fetchCostMs` | Long | 云端拉取耗时（ms，从建链到响应完整读取） |
| `isVersionUpdated` | Boolean | 版本号是否更新（> 本地缓存），true 表示触发缓存写入 |

---

#### 5.1.3 `fs_strategy_request_fail` — 云端策略请求失败

**触发时机**：`fetchFromCloud()` 中任意失败路径：
- HTTP 非 200
- 网络异常（`IOException`）
- 响应 JSON 解析异常
- slotId 不匹配
- 版本号未增长（<= 本地缓存）

**基础 ext**：`requestId`、`slotId`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `httpCode` | Integer | HTTP 响应状态码（网络异常时填 0） |
| `failReason` | String | 失败原因（见下方枚举） |
| `errorMsg` | String | 具体错误信息（异常 message） |
| `fetchCostMs` | Long | 拉取耗时（ms） |

**failReason 枚举**：

| 值 | 说明 |
|----|------|
| `NETWORK_ERROR` | 网络连接/读超时异常 |
| `HTTP_FAILED` | HTTP 非 200 |
| `PARSE_ERROR` | JSON 解析失败 |
| `SLOT_ID_MISMATCH` | 云端返回的 slotId 与请求不一致 |
| `VERSION_STALE` | 版本号未增长（<= 本地缓存版本） |

---

#### 5.1.4 `fs_strategy_merge_suc` — 策略配置合并完成

**触发时机**：`getMergedUnitConfig()` 最终返回有效 `AdUnitConfig` 时（无论来源是云端缓存还是 default JSON 兜底，或是首次云端拉取成功后的新配置）。

> **注意**：合并失败（`AdUnitConfig` 为 null）不会触发此事件，直接走 `fs_request_fail (CONFIG_NULL)`。

**基础 ext**：`requestId`、`slotId`、`strategyId`、`strategyType`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `configSource` | String | 最终配置来源：`CLOUD`（云端/SP缓存）/ `DEFAULT_JSON`（本地兜底 JSON） |
| `cloudVersion` | Long | 云配置版本号，来源为 DEFAULT_JSON 时为 0 |
| `sourceCount` | Integer | 配置中启用的广告源数量（扁平后） |
| `stageCount` | Integer | 策略阶段数 |
| `mergeCostMs` | Long | 从进入配置加载到合并完成的总耗时（ms） |

---

### 5.2 策略调度事件（Strategy）

#### 5.2.1 `fs_strategy_exec` — 策略执行开始（覆盖所有策略类型）

**触发时机**：具体策略实例（`BiddingStrategy`/`WaterfallStrategy`/`HybridStrategy`）的 `execute(context, params, unitConfig, callback)` 调用前。

> **`strategyType` 区分策略**：ext 中 `strategyType` 字段取值 `BIDDING`/`WATERFALL`/`HYBRID`，不再为各策略单独设入口事件。Waterfall 的「源收集排序完成」和 Hybrid 的「进入新阶段」均在策略内部通过此事件统一标记开始。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `strategyImpl` | String | 策略实现类名 |
| `chainTimeoutMs` | Long | 宏观超时时间（ms） |
| `firstStageType` | String | 第一阶段策略类型（Hybrid 场景有意义） |
| `firstStageSourceCount` | Integer | 第一阶段广告源数量 |
| `firstStageTimeoutMs` | Long | 第一阶段超时（ms） |

---

#### 5.2.2 `fs_strategy_exec_suc` — 策略执行成功

**触发时机**：策略层 `notifyAdLoaded(UnionAdResponse)` 调用，即 `onAdLoaded()` 回调触发前。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`bidFloor`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `winnerEcpm` | Double | 胜出广告的出价（单位：分） |
| `bidRank` | Integer | 竞价排名（1 = 最高价） |
| `totalBids` | Integer | 参与比价的报价总数 |
| `fromCache` | Boolean | 是否来自竞败缓存 |
| `execCostMs` | Long | 从 `fs_strategy_exec` 到执行成功的耗时（ms） |

---

#### 5.2.3 `fs_strategy_exec_fail` — 策略执行失败

**触发时机**：策略层 `notifyNoFill(reason)` 调用，即 `onNoFill()` 回调触发前。或 slotId 校验失败直接拒绝时；或配置合并失败（`AdUnitConfig` 为 null）时。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `failReason` | String | 策略层失败原因（见下方枚举） |
| `execCostMs` | Long | 从 `fs_strategy_exec` 到执行失败的耗时（ms） |
| `failedSourceCount` | Integer | 尝试过但失败的广告源数量 |
| `bidResponseCount` | Integer | 竞价阶段收集到的有效报价数（Bidding 场景，0 = 全部失败） |

**failReason 枚举**：

| 值 | 说明 |
|----|------|
| `SLOT_ID_MISMATCH` | 请求 slotId 与配置 slotId 不一致 |
| `NO_SOURCES` | 配置中无启用的广告源 |
| `NO_VALID_BIDS` | 竞价完成但无有效报价 |
| `ALL_SOURCES_FAILED` | 瀑布流全部源尝试失败 |
| `BIDDING_TIMEOUT` | 竞价阶段超时且无缓存报价 |
| `CHAIN_TIMEOUT` | 宏观超时 |
| `CHAIN_CANCELLED` | 外部取消 |

---

### 5.3 请求级事件（单广告源粒度）

> **全部到单广告源粒度**：每个广告源（由 `sdkType` + `adUnitId` 唯一定位）独立上报以下事件。ext 中 `sdkType`/`sdkName`/`adUnitId`/`bidFloor`/`strategyType` 必有值。
>
> **去重规则**：
> - 总请求数（loadAd 总次数）= `COUNT(DISTINCT requestId) FROM fs_request`
> - 单广告源请求数 = `COUNT(DISTINCT requestId) WHERE sdkType=X AND adUnitId=Y FROM fs_request`
> - 单广告源填充率 = `COUNT(fs_request_suc) / COUNT(fs_request)`（按 sdkType+adUnitId 分组）
> - 单广告源失败率 = `COUNT(fs_request_fail) / COUNT(fs_request)`（同上）
> - 展示率 = `COUNT(fs_show) / COUNT(fs_request_suc)`（按 sdkType+adUnitId 分组）
> - 点击率 = `COUNT(fs_click) / COUNT(fs_show)`（同上）

#### 5.3.1 `fs_request` — 单广告源请求发起

**触发时机**：每个广告源发起加载请求时。Waterfall 策略下按优先级串行逐个触发；Bidding 策略下并行触发所有 bidder。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`bidFloor`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `sourceIndex` | Integer | 广告源在策略中的序号（Waterfall: 按优先级排序后；Bidding: 按 bidFloor 排序后。从 0 开始） |
| `totalSources` | Integer | 该策略阶段总广告源数 |
| `sourceTimeoutMs` | Long | 该源配置的超时（ms） |

**示例**：
```json
{
  "requestId": "a1b2c3d4e5f6...",
  "slotId": "10000001",
  "adFormat": "SPLASH",
  "strategyId": "default",
  "strategyType": "WATERFALL",
  "sdkType": "PANGLE",
  "sdkName": "pangle_splash_1",
  "adUnitId": "8877887788",
  "bidFloor": 300,
  "cTime": 1723364803000,
  "sourceIndex": 0,
  "totalSources": 5,
  "sourceTimeoutMs": 3000
}
```

---

#### 5.3.2 `fs_request_suc` — 单广告源加载成功

**触发时机**：单个广告源 `onLoadResult(Success)` 回调时。无论该源是否为最终胜出源（`isWinner` 标记），均上报。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`bidFloor`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `sourceIndex` | Integer | 广告源序号（同 fs_request） |
| `ecpm` | Double | 广告出价（单位：分） |
| `isWinner` | Boolean | 是否成功抢占 adLoadedFlipped（真正触发 `onAdLoaded` 回调的源） |
| `fromCache` | Boolean | 是否来自竞败缓存 |
| `sourceCostMs` | Long | 该源请求耗时（ms，从 fs_request 到本事件） |
| `totalSources` | Integer | 该策略阶段总广告源数 |

---

#### 5.3.3 `fs_request_fail` — 单广告源加载失败

**触发时机**：
- 广告源 `onLoadResult(Failure)` 回调
- 广告源初始化失败（`onInitFailure`）
- 未找到对应适配器（`resolveAdapter` 返回 null）
- 超时被跳过（`timeoutFlipped=true`）
- 外部取消（`onCancel`）

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`bidFloor`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `sourceIndex` | Integer | 广告源序号 |
| `failReason` | String | 失败原因（见下方枚举） |
| `errorCode` | Integer | Adapter 返回的错误码 |
| `errorMsg` | String | Adapter 返回的错误信息 |
| `sourceCostMs` | Long | 该源耗时（ms，初始化+请求） |
| `totalSources` | Integer | 该策略阶段总广告源数 |
| `isLastSource` | Boolean | 是否为最后一个源 |

**failReason 枚举**：

| 值 | 说明 |
|----|------|
| `LOAD_FAILED` | 广告源加载失败（Adapter 返回错误） |
| `INIT_FAILED` | 广告源 SDK 初始化失败 |
| `NO_ADAPTER` | 未找到对应 SDK 适配器 |
| `TIMEOUT` | 宏观超时，源未完成即被跳过 |
| `CANCELLED` | 外部取消，源未完成即被跳过 |

---

### 5.4 竞价级事件（Bidding 专属）

#### 5.4.1 `fs_bid` — 开始竞价

**触发时机**：`BiddingStrategy.performParallelBidding()` 中遍历竞价广告源时，每个广告源启动竞价前触发。此时 `AdSourceConfig` 可获取，`sdkType`/`sdkName`/`adUnitId`/`bidFloor` 均有值。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`bidFloor`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `bidderCount` | Integer | 参与竞价的广告源数量 |
| `chainTimeoutMs` | Long | 竞价宏观超时（ms） |
| `hasCacheBid` | Boolean | 竞败缓存中是否有可复用的广告 |

---

#### 5.4.2 `fs_bid_suc` — 单源竞价成功

**触发时机**：`BiddingStrategy.handleBidSuccess()` 中，广告源通过底价过滤且 isReady=true，成功加入 `bidResponses` 时。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`bidFloor`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `ecpm` | Double | 竞价出价（单位：分） |
| `bidRank` | Integer | 当前报价在已收集 bids 中的临时排名 |
| `fromCache` | Boolean | 是否来自竞败缓存 |
| `isReady` | Boolean | 广告是否就绪 |

---

#### 5.4.3 `fs_bid_fail` — 单源竞价失败

**触发时机**：
- Adapter 回调 `onLoadFailed()` 时
- `handleBidSuccess()` 中被过滤（超时/未就绪/低于底价）时
- Adapter 初始化失败时

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`bidFloor`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `bidFailReason` | String | 竞败原因（见 `BidLossReason` 常量，下方完整枚举） |
| `ecpm` | Double | 竞价出价（有返回但被过滤时才有值，如低于底价） |
| `errorCode` | Integer | Adapter 返回的错误码（`onLoadFailed` 场景） |
| `errorMsg` | String | Adapter 返回的错误信息 |

**bidFailReason 枚举**（对应 `BidLossReason.java`）：

| 值 | 层级 | 说明 |
|----|------|------|
| `NO_AD` | 适配器层 | SDK 无广告返回 |
| `LOAD_FAILED` | 适配器层 | SDK 加载失败 |
| `CACHE_FAILED` | 适配器层 | SDK 素材缓存失败 |
| `TIMEOUT` | 适配器层 | SDK 自身请求超时 |
| `LOST_TO_HIGHER_BID` | 链路层 | 竞价返回但 ecpm 低于最高价 |
| `LOST_BELOW_FLOOR` | 链路层 | 竞价返回但 ecpm 低于底价 |
| `LOST_NOT_READY` | 链路层 | 广告已返回但未就绪（素材失效/过期） |
| `BIDDING_TIMEOUT` | 链路层 | 广告返回时已超竞价窗口 |
| `CHAIN_TIMEOUT` | 链路层 | 聚合链路整体超时 |
| `CHAIN_CANCELLED` | 链路层 | 聚合链路被外部取消 |

---

### 5.5 展示级事件（单广告源粒度）

#### 5.5.1 `fs_show` — 广告曝光

**触发时机**：业务方调用展示方法（如 `splashAd.show()`、`interstitialAd.show()`），广告实际渲染到屏幕时。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `ecpm` | Double | 广告出价（单位：分） |
| `fromCache` | Boolean | 是否来自竞败缓存 |
| `showCostMs` | Long | 从 `fs_request_suc` 到 `fs_show` 的耗时（ms） |
| `isReady` | Boolean | 展示时广告是否就绪 |

---

#### 5.5.2 `fs_click` — 广告点击

**触发时机**：广告被点击时（由三方 SDK 的点击回调触发）。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `ecpm` | Double | 广告出价（单位：分） |
| `clickToImpressionGap` | Long | 从曝光到点击的间隔（ms） |

---

#### 5.5.3 `fs_close` — 广告关闭

**触发时机**：广告被关闭/跳过时。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `closeAction` | String | 关闭动作：`USER_CLOSE`（用户关闭）/ `AUTO_CLOSE`（自动关闭）/ `SKIP`（跳过） |
| `displayDurationMs` | Long | 广告展示时长（ms，从曝光到关闭） |

---

#### 5.5.4 `fs_video_complete` — 视频播放完成

**触发时机**：激励视频广告播放完成时（仅 `REWARDED_VIDEO` 格式）。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `videoDurationMs` | Long | 视频总时长（ms） |
| `watchedDurationMs` | Long | 实际观看时长（ms） |
| `rewardVerified` | Boolean | 是否已验证发奖 |

---

#### 5.5.5 `fs_reward` — 激励视频发奖

**触发时机**：激励视频完成播放且符合发奖条件时。

**基础 ext**：`requestId`、`slotId`、`adFormat`、`strategyId`、`strategyType`、`sdkType`、`sdkName`、`adUnitId`、`cTime`

**专属 ext**：

| Key | 类型 | 说明 |
|-----|------|------|
| `rewardAmount` | Integer | 奖励数量 |
| `rewardName` | String | 奖励名称 |

---

## 6. 埋点接入指南

### 6.1 事件清单汇总

> 全量 18 个事件见 [第 2 节 埋点事件](#2-埋点事件)。

### 6.2 链路时序图

```
时间轴 ──────────────────────────────────────────────────────►

STRATEGY_REQUEST: fs_strategy_request ──► fs_strategy_request_suc/fail
                    ──► fs_strategy_merge_suc ──┐
                                               │
STRATEGY:                 fs_strategy_exec ────┤
                            (strategyType 标识) │
                                               │
PER SOURCE:               ┌── fs_request(source=0) ──► _suc/_fail
                           ├── fs_request(source=1) ──► _suc/_fail
                           ├── fs_request(source=2) ──► _suc/_fail
                           │     │
                           │     ├── Bidding 源额外触发:
                           │     └── fs_bid ──► _suc/_fail
                           │
                           └── ...  ──► _suc/_fail
                                               │
                            fs_strategy_exec_suc / _exec_fail
                                               │
PER SOURCE:                                    fs_show ──► fs_click
(DISPLAY)                                                   ──► fs_close
                                                   (fs_video_complete ──► fs_reward)
```

### 6.3 接入示例

```java
// 1. 业务方实现 FsAdTracker
public class MyAppTracker implements FsAdTracker {
    @Override
    public void trackEvent(String eventId, Map<String, Object> ext) {
        // 接入自有埋点系统（如神策、GrowingIO、自建等）
        MyAnalytics.log(eventId, ext);
    }
}

// 2. SDK 初始化时注入
FsUnionSDK.initialize(context, new FsUnionSDK.Config.Builder()
        .appId("your_app_id")
        .appName("YourApp")
        .tracker(new MyAppTracker())
        .build(), null);

// 3. SDK 内部自动埋点（业务方无感知）
// 每次 loadAd 调用后，SDK 自动按链路顺序触发上述事件
```

### 6.4 requestId 生成规则

#### 生成算法

```
requestId = MD5( packageName + "-" + appId + "-" + nanoTimestamp )
```

**三要素说明**：

| 要素 | 来源 | 说明 |
|------|------|------|
| `packageName` | `Context.getPackageName()` | 应用包名，隔离不同 App 的 requestId 冲突 |
| `appId` | `FsUnionSDK.Config.getAppId()` | 聚合 SDK 的 App ID，隔离同一设备多业务场景 |
| `nanoTimestamp` | `System.nanoTime()` | 纳秒级时间戳，同一 App 内高并发不碰撞 |

#### 唯一性保证

- **包名隔离**：不同应用生成的 requestId 完全不同
- **AppId 隔离**：同一应用不同业务场景的 requestId 不会碰撞
- **纳秒精度**：`System.nanoTime()` 提供纳秒级单调递增，同一 JVM 同一纳秒内多次调用概率极低
- **MD5 散列**：32 位十六进制字符串，长度固定、URL 安全、无特殊字符

#### Java 生成算法

```java
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 生成全链路唯一 requestId。
 *
 * <p>算法：MD5(packageName + "-" + appId + "-" + nanoTime)</p>
 *
 * <p>唯一性保证：包名 + AppId 空间隔离 + 纳秒时间戳时间隔离 + MD5 散列</p>
 *
 * @param packageName 应用包名，通过 {@link Context#getPackageName()} 获取
 * @param appId       聚合 SDK App ID，通过 {@link FsUnionSDK.Config#getAppId()} 获取
 * @return 32 位小写十六进制 requestId，保证全链路唯一
 */
public static String generateRequestId(String packageName, String appId) {
    // 纳秒级时间戳，同一 JVM 内单调递增，高并发也不碰撞
    long nanoTime = System.nanoTime();

    // 拼接原始字符串：包名-业务ID-纳秒
    String raw = packageName + "-" + appId + "-" + nanoTime;

    try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    } catch (NoSuchAlgorithmException e) {
        // MD5 算法 JVM 保证可用，此路径理论上不可达
        throw new RuntimeException("MD5 not available", e);
    }
}
```

**示例输出**：
```
// packageName = "com.ima.fs.unionad", appId = "your_app_id"
// nanoTime = 1723364803000123456
// requestId = "f3a1b9c04e2d7f6a81d5c2e3b4f8a901"
```

### 6.5 ext 构建工具

```java
/**
 * 基础 ext 构建器，链路各阶段复用。
 */
public class ExtBuilder {
    private final Map<String, Object> ext = new HashMap<>();

    /**
     * 填充基础参数（全链路通用）。
     */
    public ExtBuilder base(String requestId, String slotId, AdFormat adFormat,
                           String strategyId, StrategyType strategyType) {
        ext.put("requestId", requestId);
        ext.put("slotId", slotId);
        if (adFormat != null) ext.put("adFormat", adFormat.name());
        if (strategyId != null) ext.put("strategyId", strategyId);
        if (strategyType != null) ext.put("strategyType", strategyType.name());
        ext.put("cTime", System.currentTimeMillis());
        return this;
    }

    /**
     * 填充源级参数。
     */
    public ExtBuilder source(AdSourceConfig sourceConfig) {
        if (sourceConfig == null) return this;
        if (sourceConfig.getSdkType() != null) {
            ext.put("sdkType", sourceConfig.getSdkType().getSdkName());
        }
        ext.put("sdkName", sourceConfig.getSdkName());
        ext.put("adUnitId", sourceConfig.getAdUnitId());
        ext.put("bidFloor", sourceConfig.getBidFloor());
        return this;
    }

    public ExtBuilder put(String key, Object value) {
        if (value != null) {
            ext.put(key, value);
        }
        return this;
    }

    public Map<String, Object> build() {
        return Collections.unmodifiableMap(ext);
    }
}
```

---

## 附录：事件与代码触发点对照表

| eventId | 触发类 | 触发方法 |
|---------|--------|----------|
| `fs_strategy_request` | `CloudStrategyManager` | `fetchFromCloud()` 中 HTTP 请求发出前 |
| `fs_strategy_request_suc` | `CloudStrategyManager` | `fetchFromCloud()` HTTP 200 + 版本更新后 |
| `fs_strategy_request_fail` | `CloudStrategyManager` | `fetchFromCloud()` 任意失败路径 |
| `fs_strategy_merge_suc` | `CloudStrategyManager` | `getMergedUnitConfig()` 返回有效 config 时 |
| `fs_strategy_exec` | `BaseStrategy` 子类 | `execute()` 调用前 |
| `fs_strategy_exec_suc` | `BaseStrategy` | `notifyAdLoaded()` |
| `fs_strategy_exec_fail` | `BaseStrategy` / `AdStrategyManager` | `notifyNoFill()` / slotId 校验失败 / 配置空 |
| `fs_request` | `BaseStrategy` 子类 | 每个广告源发起请求时 |
| `fs_request_suc` | `BaseStrategy` 子类 | 单广告源 `onLoadResult(Success)` |
| `fs_request_fail` | `BaseStrategy` 子类 | 单广告源 `onLoadResult(Failure)` / 初始化失败 / 超时 |
| `fs_bid` | `BiddingStrategy` | `performParallelBidding()` |
| `fs_bid_suc` | `BiddingStrategy` | `handleBidSuccess()` 通过过滤后 |
| `fs_bid_fail` | `BiddingStrategy` | `handleBidSuccess()` 被过滤 / `doBid` 的 `onLoadFailed` |
| `fs_show` | `Fs*AdManager` / 业务方 | 展示方法调用后 |
| `fs_click` | `AdEventListener` | 点击回调 |
| `fs_close` | `AdEventListener` | 关闭回调 |
| `fs_video_complete` | `AdEventListener` | 视频完成回调 |
| `fs_reward` | `AdEventListener` | 发奖回调 |
