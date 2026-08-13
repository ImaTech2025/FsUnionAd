package com.ima.union.adapters.gdt;

import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.ViewGroup;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.FsUnionListenerBridge;
import com.ima.union.core.adapter.SplashAdAdapter;
import com.ima.union.core.adapter.SplashAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.qq.e.ads.splash.SplashAD;
import com.qq.e.ads.splash.SplashADListener;
import com.qq.e.comm.util.AdError;

/**
 * 优量汇开屏广告适配器。
 *
 * <p>设计要点：{@code SplashAD} 实例在 {@link #doRequest} 中创建并加载，
 * 存入 {@link UnionAdResponse#getNativeAd()} 供 {@link #showSplash} 复用。
 * 同一 {@code SplashAD} 实例 + 同一 {@link GdtSplashCallback} 监听器贯穿加载和展示全生命周期，
 * 确保曝光、点击等回调正确送达外部监听器。</p>
 */
public class GdtSplashAdapter extends GdtBaseAdapter implements SplashAdAdapter {

    private static final String TAG = "GdtSplashAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String placeId = resolvePlaceId(sourceConfig);
        FsLogger.d(TAG, "▶ request[Splash]: sdkName=" + sdkName + " placeId=" + placeId
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());

        try {
            int fetchDelay = (int) sourceConfig.getTimeout();
            if (fetchDelay <= 0) fetchDelay = 3000;

            // 桥接器：请求阶段注入 SDK 回调，展示阶段绑定 SplashAdListener
            FsUnionListenerBridge<SplashAdListener> showBridge = new FsUnionListenerBridge<>();

            GdtSplashCallback splashCallback = new GdtSplashCallback(sourceConfig, callback, showBridge);
            SplashAD splashAd = new SplashAD(context, placeId, splashCallback, fetchDelay);
            splashCallback.bindAd(splashAd);
        } catch (Exception e) {
            FsLogger.e(TAG, "request[Splash] exception: " + e.getMessage(), e);
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
        }
    }

    @Override
    public void showSplash(Context context, UnionAdResponse response, ViewGroup container, SplashAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "showSplash: response is null");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        FsLogger.d(TAG, "▶ showSplash: sourceId=" + response.getSdkName()
                + " container=" + (container != null ? container.getClass().getSimpleName() : "null"));
        if (container == null) {
            if (listener != null) listener.onAdError(FsAdErrorCode.CONTAINER_NULL, FsAdErrorCode.buildMsg("展示容器为空"));
            return;
        }

        Object nativeAd = response.getNativeAd();
        if (!(nativeAd instanceof SplashAD)) {
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }

        try {
            // 从 extra 取出桥接器并绑定当前 listener
            java.util.Map<String, Object> extra = response.getExtra();
            if (extra != null) {
                FsUnionListenerBridge<SplashAdListener> showBridge = (FsUnionListenerBridge<SplashAdListener>) extra.get(FsUnionListenerBridge.EXTRA_KEY);
                if (showBridge != null) showBridge.bind(listener);
            }
            SplashAD splashAd = (SplashAD) nativeAd;
            container.removeAllViews();
            splashAd.showAd(container);
            FsLogger.d(TAG, "showSplash");
        } catch (Exception e) {
            FsLogger.e(TAG, "showSplash exception: " + e.getMessage(), e);
            if (listener != null) listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常", response.getSdkName(), 0, e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  内部 Handler：统一加载 + 展示回调路由
    // ════════════════════════════════════════════════════════════════

    /**
     * GDT 开屏广告统一监听器。
     *
     * <p>加载阶段：{@code onADLoaded} / {@code onNoAD} 路由到 {@link AdCallback}。
     * 展示阶段：{@code onADPresent} / {@code onADExposure} / {@code onADClicked} / {@code onADDismissed}
     * 通过 {@link FsUnionListenerBridge} 路由到展示阶段绑定的 {@link SplashAdListener}。</p>
     *
     * <p>{@code onNoAD} 是 GDT 的"无广告"回调，本质为<b>请求失败</b>，
     * 一律通过 {@code callback.onLoadFailed()} 回调，不路由到展示阶段的 {@code onAdError}。</p>
     */
    private class GdtSplashCallback implements SplashADListener {
        private final AdSourceConfig sourceConfig;
        private final AdCallback callback;
        private final FsUnionListenerBridge<SplashAdListener> showBridge;
        private SplashAD splashAd;

        GdtSplashCallback(AdSourceConfig sourceConfig, AdCallback callback, FsUnionListenerBridge<SplashAdListener> showBridge) {
            this.sourceConfig = sourceConfig;
            this.callback = callback;
            this.showBridge = showBridge;
        }

        void bindAd(SplashAD ad) {
            this.splashAd = ad;
        }

        // ── 加载阶段回调 ──
        @Override
        public void onADLoaded(long expireTimestamp) {
            FsLogger.d(TAG, "request[Splash]: onADLoaded expireTs=" + expireTimestamp);
            if (splashAd == null) return;
            java.util.Map<String, Object> extra = new java.util.HashMap<>();
            extra.put(FsUnionListenerBridge.EXTRA_KEY, showBridge);
            UnionAdResponse response = buildResponse(sourceConfig, AdFormat.SPLASH, splashAd, 0, extra);
            if (callback != null) {
                callback.onLoaded(response);
                callback.onCachedSuccess(response);
            }
        }

        @Override
        public void onNoAD(AdError error) {
            FsLogger.w(TAG, "GdtSplash onNoAD: code=" + error.getErrorCode() + " msg=" + error.getErrorMsg());
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sourceConfig.getSdkName(), error.getErrorCode(), error.getErrorMsg()));
        }

        // ── 展示阶段回调 ──
        @Override
        public void onADPresent() {
            FsLogger.d(TAG, "GdtSplash.onADPresent: sourceId=" + sourceConfig.getSdkName());
        }

        @Override
        public void onADExposure() {
            FsLogger.i(TAG, "GdtSplash.onADExposure: sourceId=" + sourceConfig.getSdkName());
            SplashAdListener l = showBridge.get();
            if (l != null) l.onAdShow();
        }

        @Override
        public void onADClicked() {
            FsLogger.i(TAG, "GdtSplash.onADClicked: sourceId=" + sourceConfig.getSdkName());
            SplashAdListener l = showBridge.get();
            if (l != null) l.onAdClick();
        }

        @Override
        public void onADDismissed() {
            FsLogger.i(TAG, "GdtSplash.onADDismissed: sourceId=" + sourceConfig.getSdkName());
            SplashAdListener l = showBridge.get();
            if (l != null) {
                l.onAdClose();
                showBridge.clear();
            }
        }

        @Override
        public void onADTick(long millisUntilFinished) {
        }
    }
}
