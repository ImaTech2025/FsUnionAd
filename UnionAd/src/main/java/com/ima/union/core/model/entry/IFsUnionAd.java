package com.ima.union.core.model.entry;

import com.ima.union.core.model.AdSdkType;

/**
 * 聚合广告对象统一接口 — 暴露给业务方的对外视图。
 * <p>业务方通过 {@link com.ima.union.manager.splash.FsSplashAdManager#loadAd} 等入口拿到的就是此接口类型，
 * 屏蔽内部 {@code FsUnion*Ad} 具体实现类的差异，使广告对象与适配器实现解耦。</p>
 *
 * <p>所有广告格式（开屏/插屏/激励视频/信息流自渲染/信息流模板）共享以下通用能力：</p>
 * <ul>
 *   <li>查询广告基本信息（eCPM、SDK 类型、SDK 名称、广告位 ID）</li>
 *   <li>由各格式 Manager 在加载回调中统一暴露的对象；展示和事件回调由各子接口提供</li>
 * </ul>
 */
public interface IFsUnionAd {

    /**
     * 返回该广告的 eCPM 价格（单位：分）。
     */
    double getEcpm();

    /**
     * 返回该广告的 SDK 平台类型。
     */
    AdSdkType getSdkType();

    /**
     * 返回该广告的 SDK 名称（穿山甲 / 优量汇 / 百青藤 / 飞梭 / 自定义平台名等）。
     * <p>优先取策略配置中的 {@code sdkName} 字段，为空时取 {@link AdSdkType} 枚举的默认名。</p>
     */
    String getSdkName();

    /**
     * 返回该广告位 ID（业务方调用广告请求时使用的 slotId）。
     */
    String getSlotId();

    /**
     * 返回该广告实际来自的广告源 adUnitId（来自 {@code AdSourceConfig.adUnitId}）。
     * <p>与 {@link #getSlotId()} 的区别：slotId 是业务方维度，一个 slotId 可对应多家广告源；
     * adUnitId 是广告源维度，标识具体哪家广告平台 / 哪个 code 出的广告。
     * <b>adUnitId 在竞价链路中作为唯一确定性标识</b>，用于在 {@code BiddingStrategy} 中反查对应的 {@code AdSourceConfig}。</p>
     */
    String getAdUnitId();

    /**
     * 检查广告当前是否处于就绪状态（可正常展示）。
     *
     * <p><b>实时检查</b>：每次调用都会重新查询当前状态，而非加载时的快照值。
     * 例如百青藤自渲染广告 {@code NativeResponse.isReady()} 在素材过期后返回 {@code false}。</p>
     *
     * <p><b>务必在调用 {@code show()} 前调用此方法做二次确认</b>，避免展示已失效的广告素材。
     * 如果返回 {@code false}，应放弃此次展示并视情况重新加载广告。</p>
     *
     * @return true 表示广告可正常展示，false 表示已失效/未就绪
     */
    boolean isReady();
}
