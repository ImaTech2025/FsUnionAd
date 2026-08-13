package com.ima.union.core.adapter;

/**
 * 展示/交互阶段事件监听 — 广告加载成功后，在展示和交互阶段触发的回调。
 *
 * <p>与 {@link AdCallback} 的区别：</p>
 * <ul>
 *   <li>{@link AdCallback} 服务于<b>请求/加载阶段</b>（onLoaded / onCachedSuccess / onLoadFailed）</li>
 *   <li>本接口服务于<b>展示/交互阶段</b>（onAdShow / onAdClick / onAdClose / onAdError）</li>
 * </ul>
 *
 * <p>{@link #onAdError} 表示广告已加载成功，但在<b>展示或交互过程中</b>出错
 * （如容器为空、广告对象无效、渲染失败、视频播放错误等）。
 * 与 {@link AdCallback#onLoadFailed} 的<b>请求失败</b>语义不同，不可混用。</p>
 */
public interface AdEventListener {
    /** 广告曝光展示 */
    default void onAdShow() {}

    /** 广告被点击 */
    default void onAdClick() {}

    /** 广告关闭（用户手动关闭或倒计时结束） */
    default void onAdClose() {}

    /**
     * 展示/交互阶段错误 — 广告已加载，但展示或交互过程中出错。
     * <p>典型场景：容器为 null、广告对象失效、渲染异常、视频播放失败等。</p>
     * <p><b>不是</b>请求失败，请求失败请使用 {@link AdCallback#onLoadFailed}。</p>
     */
    default void onAdError(int errorCode, String errorMsg) {}
}
