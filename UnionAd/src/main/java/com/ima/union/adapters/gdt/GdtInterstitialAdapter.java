package com.ima.union.adapters.gdt;

import com.ima.union.utils.FsLogger;

import android.app.Activity;
import android.content.Context;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.InterstitialAdAdapter;
import com.ima.union.core.adapter.InterstitialAdListener;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.FsUnionListenerBridge;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.qq.e.ads.interstitial2.UnifiedInterstitialAD;
import com.qq.e.ads.interstitial2.UnifiedInterstitialADListener;
import com.qq.e.comm.util.AdError;

/**
 * 优量汇插屏广告适配器。
 */
public class GdtInterstitialAdapter extends GdtBaseAdapter implements InterstitialAdAdapter {

    private static final String TAG = "GdtInterstitialAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String placeId = resolvePlaceId(sourceConfig);
        FsLogger.d(TAG, "▶ request[Interstitial]: sdkName=" + sdkName + " placeId=" + placeId
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());

        try {
            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            // 使用 holder 解决匿名类中引用外层局部变量的"可能尚未初始化"问题
            // 桥接器：请求阶段注入 SDK 回调，展示阶段绑定 InterstitialAdListener
            FsUnionListenerBridge<InterstitialAdListener> showBridge = new FsUnionListenerBridge<>();

            final UnifiedInterstitialAD[] interstitialADS = new UnifiedInterstitialAD[1];
            final UnionAdResponse[] response = new UnionAdResponse[1];
            interstitialADS[0] = new UnifiedInterstitialAD(
                    context instanceof Activity ? (Activity) context : null,
                    placeId,
                    new UnifiedInterstitialADListener() {
                        @Override
                        public void onADReceive() {
                            timeoutCtrl.finish();
                            double ecpm = interstitialADS[0].getECPM();
                            FsLogger.d(TAG, "request[Interstitial]: onADReceive ecpm=" + ecpm);
                            java.util.Map<String, Object> extra = new java.util.HashMap<>();
                            extra.put(FsUnionListenerBridge.EXTRA_KEY, showBridge);
                            response[0] = buildResponse(sourceConfig, AdFormat.INTERSTITIAL, interstitialADS[0], (int) ecpm, extra);
                            if (callback != null) callback.onLoaded(response[0]);
                        }

                        @Override
                        public void onNoAD(AdError error) {
                            timeoutCtrl.finish();
                            FsLogger.d(TAG, "request[Interstitial]: onNoAD errorCode=" + error.getErrorCode() + " , errorMsg=" + error.getErrorMsg());
                            if (callback != null)
                                callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, error.getErrorCode(), error.getErrorMsg()));
                        }

                        @Override
                        public void onADOpened() {
                        }

                        @Override
                        public void onADExposure() {
                            InterstitialAdListener l = showBridge.get();
                            if (l != null) {
                                l.onAdShow();
                            }
                        }

                        @Override
                        public void onADClicked() {
                            InterstitialAdListener l = showBridge.get();
                            if (l != null) {
                                l.onAdClick();
                            }
                        }

                        @Override
                        public void onADClosed() {
                            InterstitialAdListener l = showBridge.get();
                            if (l != null) {
                                l.onAdClose();
                                showBridge.clear();
                            }
                        }

                        @Override
                        public void onADLeftApplication() {
                        }

                        @Override
                        public void onRenderSuccess() {
                            if (response[0] != null && callback != null)
                                callback.onCachedSuccess(response[0]);
                        }

                        @Override
                        public void onRenderFail() {
                            FsLogger.e(TAG, "GdtInterstitial.onRenderFail: render failed");
                        }

                        @Override
                        public void onVideoCached() {
                            FsLogger.d(TAG, "request[Interstitial]: onVideoCached");
                            // 视频缓存完成也可视为就绪（若 onADReceive 未触发）
                        }
                    });
            // 透传静音配置：GDT 4.561+ 通过 VideoOption 控制
            if (params.getVideoMuted()) {
                com.qq.e.ads.cfg.VideoOption option = new com.qq.e.ads.cfg.VideoOption.Builder()
                        .setAutoPlayMuted(true)
                        .setDetailPageMuted(true)
                        .build();
                interstitialADS[0].setVideoOption(option);
            }
            interstitialADS[0].loadAD();
        } catch (Exception e) {
            FsLogger.e(TAG, "request[Interstitial] exception: " + e.getMessage(), e);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
        }
    }

    @Override
    public void showInterstitial(Context context, UnionAdResponse response, InterstitialAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "showInterstitial: response is null");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        FsLogger.d(TAG, "▶ showInterstitial: sourceId=" + response.getSdkName());
        UnifiedInterstitialAD ad = (UnifiedInterstitialAD) response.getNativeAd();
        if (ad == null) {
            FsLogger.e(TAG, "showInterstitial: no ad object");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        try {
            // 从 extra 取出桥接器并绑定当前 listener，使请求阶段注册的 SDK 回调能转发事件
            java.util.Map<String, Object> extra = response.getExtra();
            if (extra != null) {
                FsUnionListenerBridge<InterstitialAdListener> showBridge = (FsUnionListenerBridge<InterstitialAdListener>) extra.get(FsUnionListenerBridge.EXTRA_KEY);
                if (showBridge != null) showBridge.bind(listener);
            }
            ad.show();
            FsLogger.d(TAG, "showInterstitial");
        } catch (Exception e) {
            FsLogger.e(TAG, "showInterstitial exception: " + e.getMessage(), e);
            if (listener != null)
                listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常", response.getSdkName(), 0, e.getMessage()));
        }
    }
}
