package com.ima.union.adapters.pangle;

import com.ima.union.utils.FsLogger;

import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.content.Context;

import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTFullScreenVideoAd;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.InterstitialAdAdapter;
import com.ima.union.core.adapter.InterstitialAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.FsAdErrorCode;

/**
 * 穿山甲插屏广告适配器（全屏视频）。
 */
public class PangleInterstitialAdapter extends PangleBaseAdapter implements InterstitialAdAdapter {


    private static final String TAG = "PangleInterstitialAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String codeId = resolveCodeId(sourceConfig);
        FsLogger.d(TAG, "▶ request[Interstitial]: sdkName=" + sdkName + " codeId=" + codeId
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());

        try {
            AdSlot adSlot = buildAdSlot(codeId, AdFormat.INTERSTITIAL, 0, 0, params);
            TTAdNative ttAdNative = createTtAdNative(context);
            if (ttAdNative == null) {
                if (callback != null)
                    callback.onLoadFailed(FsAdErrorCode.ADAPTER_CREATE_FAILED, FsAdErrorCode.buildMsg("SDK客户端创建失败", sdkName, 0, ""));
                return;
            }

            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            ttAdNative.loadFullScreenVideoAd(adSlot, new TTAdNative.FullScreenVideoAdListener() {
                @Override
                public void onError(int code, String msg) {
                    timeoutCtrl.finish();
                    if (callback != null)
                        callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, code, msg));
                }

                @Override
                public void onFullScreenVideoAdLoad(TTFullScreenVideoAd ad) {
                    FsLogger.d(TAG, "request[Interstitial]: ad loaded");
                }

                @Override
                public void onFullScreenVideoCached() {
                }

                @Override
                public void onFullScreenVideoCached(TTFullScreenVideoAd ad) {
                    double ecpm = getEcpmFromAd(ad);
                    FsLogger.d(TAG, "request[Interstitial]: cached ecpm=" + ecpm);
                    timeoutCtrl.finish();
                    UnionAdResponse response = buildResponse(sourceConfig, AdFormat.INTERSTITIAL, ad, ecpm);
                    if (callback != null) {
                        callback.onLoaded(response);
                        callback.onCachedSuccess(response);
                    }
                }
            });
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
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        FsLogger.d(TAG, "▶ showInterstitial: sourceId=" + response.getSdkName());
        TTFullScreenVideoAd ad = (TTFullScreenVideoAd) response.getNativeAd();
        if (ad == null) {
            FsLogger.e(TAG, "showInterstitial: no ad object");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        try {
            if (context instanceof Activity) {
                // Close guard: Pangle SDK 在用户跳过插屏视频时会先回调 onSkippedVideo，
                // 紧接着再回调 onAdClose，用 AtomicBoolean CAS 保证只转发第一次关闭回调。
                final AtomicBoolean closedGuard = new AtomicBoolean(false);

                ad.setFullScreenVideoAdInteractionListener(new TTFullScreenVideoAd.FullScreenVideoAdInteractionListener() {
                    @Override
                    public void onAdShow() {
                        FsLogger.d(TAG, "FullScreenVideo.onAdShow");
                        if (listener != null)
                            listener.onAdShow();
                    }

                    @Override
                    public void onAdVideoBarClick() {
                        FsLogger.d(TAG, "FullScreenVideo.onAdVideoBarClick");
                        if (listener != null)
                            listener.onAdClick();
                    }

                    @Override
                    public void onAdClose() {
                        FsLogger.d(TAG, "FullScreenVideo.onAdClose");
                        if (closedGuard.compareAndSet(false, true) && listener != null) {
                            listener.onAdClose();
                        }
                    }

                    @Override
                    public void onVideoComplete() {
                    }

                    @Override
                    public void onSkippedVideo() {
                        FsLogger.d(TAG, "FullScreenVideo.onSkippedVideo → forward as onAdClose (guarded)");
                        if (closedGuard.compareAndSet(false, true) && listener != null) {
                            listener.onAdClose();
                        }
                    }
                });
                ad.showFullScreenVideoAd((Activity) context);
                FsLogger.d(TAG, "showInterstitial");
            } else {
                if (listener != null)
                    listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("Context不是Activity"));
            }
        } catch (Exception e) {
            FsLogger.e(TAG, "showInterstitial exception: " + e.getMessage(), e);
            if (listener != null)
                listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常"));
        }
    }
}
