package com.ima.union.core.adapter;

import com.ima.union.core.model.UnionAdResponse;

/**
 * 适配器统一回调 — 合并自原 {@code AdBidCallback} + {@code AdLoadCallback}。
 *
 * <p>无论是竞价请求还是瀑布流加载请求，适配器均通过该接口回调结果：</p>
 * <ul>
 *   <li>{@link #onLoaded(UnionAdResponse)} — 广告请求成功（广告对象已就绪）</li>
 *   <li>{@link #onCachedSuccess(UnionAdResponse)} — 广告物料缓存成功（视频/大图等素材已下载）</li>
 *   <li>{@link #onLoadFailed(int, String)} — 广告请求失败</li>
 * </ul>
 *
 * <h3>onLoaded vs onCachedSuccess</h3>
 * <ul>
 *   <li><b>onLoaded</b> — 广告请求返回成功，广告对象已创建并可以展示。所有适配器在广告加载成功后
 *       回调此方法。</li>
 *   <li><b>onCachedSuccess</b> — 广告物料（视频/大图）已下载到本地。default 方法，信息流适配器
 *       不需要实现。激励视频等需要缓存通知的场景由策略层按需重写。</li>
 * </ul>
 */
public interface AdCallback {
    /**
     * 广告请求成功，广告对象已创建并可以展示。
     * 竞价场景下 response.getEcpm() 携带平台报价，瀑布流场景下为策略配置 eCPM。
     */
    void onLoaded(UnionAdResponse response);

    /**
     * 广告物料缓存成功（视频/大图等素材已下载到本地）。
     * <p>default 方法，信息流适配器无需实现。需要缓存通知的策略层可按需重写。</p>
     */
    default void onCachedSuccess(UnionAdResponse response) {
    }

    /**
     * 广告请求失败（加载阶段）。
     *
     * <p>典型场景：无填充、网络超时、请求异常、SDK 内部错误等。
     * 与 {@link AdEventListener#onAdError} 的区别：本方法表示广告<b>没加载出来</b>，
     * 而 {@code onAdError} 表示广告已加载但在<b>展示时出错</b>。</p>
     *
     * @param errorCode 错误码
     * @param errorMsg  错误描述
     */
    void onLoadFailed(int errorCode, String errorMsg);
}
