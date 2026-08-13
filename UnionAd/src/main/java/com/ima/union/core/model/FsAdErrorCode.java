package com.ima.union.core.model;

/**
 * 聚合 SDK 全局统一错误码。
 *
 * <p>定义三个层级的错误码范围，每个错误场景对应唯一的 code：</p>
 * <ul>
 *   <li><b>1001-1999</b> — Manager/Strategy 层：配置缺失、适配器解析失败、无填充等</li>
 *   <li><b>2001-2999</b> — 加载阶段（{@code AdCallback.onLoadFailed}）：SDK 未初始化、超时、返回错误等</li>
 *   <li><b>3001-3999</b> — 展示阶段（{@code AdEventListener.onAdError}）：容器空、渲染失败、播放错误等</li>
 * </ul>
 *
 * <p><b>MSG 格式</b>（通过 {@link #buildMsg} 构建）：</p>
 * <pre>
 *   {聚合层提示} | {sdkName}[{三方SDK原始code}] {三方SDK原始msg}
 * </pre>
 * <p>示例：</p>
 * <pre>
 *   SDK加载失败:无广告返回 | Pangle[40001] no ad
 *   SDK请求超时(3000ms) | GDT[0]
 *   展示错误:容器为空
 * </pre>
 *
 * <p>当错误为聚合层内部错误（无三方 SDK 参与）时，msg 仅包含聚合层提示，不带 SDK 信息。</p>
 */
public final class FsAdErrorCode {

    private FsAdErrorCode() {}

    // ════════════════════════════════════════════════════════════════
    //  Manager / Strategy 层 (1001-1999)
    //  由 AdStrategyManager / FsXxxAdManager 使用，经 onNoFill / onAdLoadError 回调
    // ════════════════════════════════════════════════════════════════

    /** 合并配置为空（cloud config + default strategy 均无有效配置） */
    public static final int CONFIG_NULL = 1001;

    /** 加载成功但无法解析对应的适配器实例 */
    public static final int ADAPTER_RESOLVE_FAILED = 1002;

    /** 所有广告源均无填充（策略层所有 source 耗尽仍无成功） */
    public static final int NO_FILL = 1003;

    /** slotId 校验不通过（请求 slotId 与配置 slotId 不一致） */
    public static final int SLOT_ID_MISMATCH = 1004;

    // ════════════════════════════════════════════════════════════════
    //  加载阶段 (2001-2999) — AdCallback.onLoadFailed
    //  由各适配器在 request/doRequest 中使用
    // ════════════════════════════════════════════════════════════════

    /** SDK 未初始化（适配器 isInitialized() 返回 false） */
    public static final int SDK_NOT_INITIALIZED = 2001;

    /** 适配器层请求超时（AdLoadTimeout 兜底触发） */
    public static final int SDK_LOAD_TIMEOUT = 2002;

    /** SDK 返回加载错误（三方 SDK 的 onError / onNoAD / onAdFail 等回调） */
    public static final int SDK_LOAD_FAILED = 2003;

    /** SDK 返回空列表（加载成功但无广告对象） */
    public static final int SDK_NO_AD_RETURNED = 2004;

    /** 请求参数无效（slotId / placeId / container 等必填参数缺失或非法） */
    public static final int REQUEST_PARAM_INVALID = 2005;

    /** 请求过程中抛出异常（未预期的 RuntimeException） */
    public static final int REQUEST_EXCEPTION = 2006;

    /** SDK 客户端对象创建失败（如 TTAdNative 创建失败） */
    public static final int ADAPTER_CREATE_FAILED = 2007;

    // ════════════════════════════════════════════════════════════════
    //  展示阶段 (3001-3999) — AdEventListener.onAdError
    //  由各适配器在 showSplash / showInterstitial / showRewardedVideo / renderFeedAd 中使用
    // ════════════════════════════════════════════════════════════════

    /** 适配器类型不匹配（adapter 未实现对应的 XxxAdAdapter 接口） */
    public static final int ADAPTER_TYPE_MISMATCH = 3001;

    /** 展示容器为空（container == null） */
    public static final int CONTAINER_NULL = 3002;

    /** 广告对象无效（response.getNativeAd() 为 null 或类型不匹配） */
    public static final int AD_OBJECT_INVALID = 3003;

    /** 展示过程抛出异常（show/render 方法未预期的 RuntimeException） */
    public static final int SHOW_EXCEPTION = 3004;

    /** 渲染失败（模板渲染 / ExpressView 创建失败） */
    public static final int RENDER_FAILED = 3005;

    /** 视频播放错误（播放器内部错误或下载失败） */
    public static final int PLAYBACK_ERROR = 3006;

    /** SDK 返回展示阶段错误（三方 SDK 的 onVideoError / onRenderFail 等） */
    public static final int SDK_SHOW_ERROR = 3007;

    // ════════════════════════════════════════════════════════════════
    //  MSG 构建工具
    // ════════════════════════════════════════════════════════════════

    /**
     * 构建带三方 SDK 信息的错误消息。
     *
     * <p>格式：{@code {聚合层提示} | {sdkName}[{sdkCode}] {sdkMsg}}</p>
     *
     * @param aggMsg 聚合层提示信息（必填）
     * @param sdkName 三方 SDK 名称（如 "Pangle"、"GDT"），可为 null
     * @param sdkCode 三方 SDK 原始错误码
     * @param sdkMsg  三方 SDK 原始错误信息，可为 null
     * @return 拼接后的完整错误消息
     */
    public static String buildMsg(String aggMsg, String sdkName, int sdkCode, String sdkMsg) {
        StringBuilder sb = new StringBuilder(aggMsg);
        if (sdkName != null && !sdkName.isEmpty()) {
            sb.append(" | ").append(sdkName).append("[").append(sdkCode).append("]");
            if (sdkMsg != null && !sdkMsg.isEmpty()) {
                sb.append(" ").append(sdkMsg);
            }
        }
        return sb.toString();
    }

    /**
     * 构建无三方 SDK 信息的错误消息（聚合层内部错误专用）。
     *
     * @param aggMsg 聚合层提示信息
     * @return 原样返回 aggMsg
     */
    public static String buildMsg(String aggMsg) {
        return aggMsg;
    }
}
