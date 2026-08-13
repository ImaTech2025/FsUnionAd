package com.ima.union.core.adapter;

import android.content.Context;

import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;

/**
 * 适配器统一接口。
 *
 * <p>无论是竞价请求还是瀑布流加载请求，调用方均通过 {@link #request(Context, AdRequestParams, AdSourceConfig, AdCallback)}
 * 一个方法发起。具体由适配器内部根据 {@link AdSourceConfig#getAdFormat()} 和
 * {@link AdAdapter#supportBidding()} 决定走竞价通道还是加载通道。</p>
 */
public interface AdAdapter {
    AdSdkType getSdkType();
    String getAdapterVersion();

    /**
     * 返回该适配器对应的 SDK 名称。
     * <p>优先级：</p>
     * <ol>
     *   <li>优先返回策略配置中的 {@code sdkName} 值</li>
     *   <li>如为空，则取 {@code AdSdkType} 枚举中的 {@code sdkName} 默认值返回</li>
     * </ol>
     * <p>内置平台适配器可直接返回 {@code null}（通过 {@code AdSourceConfig#getSdkName()} 统一获取）；</p>
     * <p>自定义适配器可返回自定义的 SDK 名称。</p>
     */
    default String getSdkName() { return null; }

    /**
     * 检测对应三方 SDK 是否已集成到当前构建中（关键类是否存在于 classpath）。
     *
     * <p>内置平台适配器通过 {@link Class#forName(String)} 反射探测各自 SDK 关键类，
     * 结果缓存于实例内，未集成时返回 {@code false}；</p>
     * <p>自定义适配器（如 {@code BaseCustomAdAdapter} 子类）认为业务方自带依赖，
     * 默认返回 {@code true}，无需探测。</p>
     *
     * <p><b>注册时校验</b>：{@code FsUnionSDK.initialize()} 注册各适配器前先调用本方法，
     * 仅当返回 {@code true} 时才注册。这样未集成的 SDK 对应适配器不会进入注册表，
     * 策略层解析该广告源时得到 {@code null} 并快速跳过，避免执行到适配器方法时
     * 因三方类缺失抛出 {@code NoClassDefFoundError} 崩溃。</p>
     *
     * @return true 表示 SDK 可用（已集成），false 表示未集成不应注册
     */
    default boolean isSdkAvailable() { return true; }

    boolean isInitialized();

    void initialize(Context context, String appId, String token, AdInitCallback callback);

    /**
     * 发起广告请求（统一方法，替代原来的 {@code bid} + {@code loadAd}）。
     *
     * <p>适配器实现应根据内部策略决定走竞价通道或加载通道，最终统一通过 {@code callback.onLoaded(UnionAdResponse)}、
     * {@code callback.onCachedSuccess(UnionAdResponse)} 或 {@code callback.onLoadFailed(...)} 回调结果。</p>
     *
     * <p>{@code AdRequestParams} 携带请求级参数（如模板尺寸 / 自渲染图片尺寸），
     * 由调用方在 loadAd 入口处传入。适配器在透传给三方 SDK 之前应自行判断
     * 字段是否有效（&lt;=0 时不传）。</p>
     */
    void request(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback);

    /**
     * 向后兼容的便捷重载：调用方未提供尺寸参数时使用。默认委托到
     * {@link #request(Context, AdRequestParams, AdSourceConfig, AdCallback)}，仅传 sourceConfig。
     */
    default void request(Context context, AdSourceConfig sourceConfig, AdCallback callback) {
        AdRequestParams params = new AdRequestParams.Builder()
                .slotId(sourceConfig.getAdUnitId() != null ? sourceConfig.getAdUnitId() : "")
                .build();
        request(context, params, sourceConfig, callback);
    }

    /**
     * 是否支持 Bidding。默认 false（瀑布流模式）。
     */
    default boolean supportBidding() { return false; }

    /**
     * 竞败上报。各平台适配器按需覆写，将竞败通知给对应 SDK。
     *
     * <p>策略层（{@code BiddingStrategy} 等）在决策出竞败结果后，
     * 通过 {@code response.getAdapter().reportBidFail(nativeAd, reason)} 调用，
     * 由各适配器自行处理平台特定的竞败上报逻辑。</p>
     *
     * <p>默认空实现：不支持的平台（如穿山甲、优量汇）无需上报。</p>
     *
     * @param nativeAd 各 SDK 广告对象
     * @param reason   竞败原因，使用 {@link com.ima.union.core.model.BidLossReason} 统一常量
     */
    default void reportBidFail(Object nativeAd, String reason) {
        // 默认空实现
    }

    void destroy();
}
