package com.ima.union.adapters.pangle;

import com.bytedance.sdk.openadsdk.CSJSplashCloseType;
import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.ViewGroup;

import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.CSJAdError;
import com.bytedance.sdk.openadsdk.CSJSplashAd;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.SplashAdAdapter;
import com.ima.union.core.adapter.SplashAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.FsAdErrorCode;

/**
 * 穿山甲开屏广告适配器。
 */
public class PangleSplashAdapter extends PangleBaseAdapter implements SplashAdAdapter {


    private static final String TAG = "PangleSplashAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String codeId = resolveCodeId(sourceConfig);
        FsLogger.d(TAG, "▶ request[Splash]: sdkName=" + sdkName + " codeId=" + codeId
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());


        try {
            final int timeoutMs = (int) sourceConfig.getTimeout();
            AdSlot adSlot = buildAdSlot(codeId, AdFormat.SPLASH, 0, 0, params);
            TTAdNative ttAdNative = createTtAdNative(context);
            if (ttAdNative == null) {
                if (callback != null)
                    callback.onLoadFailed(FsAdErrorCode.ADAPTER_CREATE_FAILED, FsAdErrorCode.buildMsg("SDK客户端创建失败", sdkName, 0, ""));
                return;
            }
            ttAdNative.loadSplashAd(adSlot, new TTAdNative.CSJSplashAdListener() {
                @Override
                public void onSplashLoadSuccess(CSJSplashAd ad) {
                    FsLogger.d(TAG, "request[Splash]: onSplashLoadSuccess");
                }

                @Override
                public void onSplashLoadFail(CSJAdError error) {
                    FsLogger.w(TAG, "request[Splash]: onSplashLoadFail code=" + error.getCode());
                    if (callback != null)
                        callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, error.getCode(), error.getMsg()));
                }

                @Override
                public void onSplashRenderSuccess(CSJSplashAd ad) {
                    double ecpm = getEcpmFromAd(ad);
                    FsLogger.d(TAG, "request[Splash]: onSplashRenderSuccess ecpm=" + ecpm);
                    UnionAdResponse response = buildResponse(sourceConfig, AdFormat.SPLASH, ad, ecpm);
                    if (callback != null) {
                        callback.onLoaded(response);
                        callback.onCachedSuccess(response);
                    }
                }

                @Override
                public void onSplashRenderFail(CSJSplashAd ad, CSJAdError error) {
                    if (callback != null)
                        callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, error.getCode(), error.getMsg()));
                }
            }, timeoutMs);
        } catch (Exception e) {
            FsLogger.e(TAG, "request[Splash] exception: " + e.getMessage(), e);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
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
            if (listener != null)
                listener.onAdError(FsAdErrorCode.CONTAINER_NULL, FsAdErrorCode.buildMsg("展示容器为空"));
            return;
        }
        CSJSplashAd ad = (CSJSplashAd) response.getNativeAd();
        if (ad == null) {
            FsLogger.e(TAG, "showSplash: no splash ad object");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }

        try {
            container.removeAllViews();
            ad.setSplashAdListener(new CSJSplashAd.SplashAdListener() {
                @Override
                public void onSplashAdShow(CSJSplashAd splashAd) {
                    FsLogger.i(TAG, "CSJSplashAd.onSplashAdShow: sourceId=" + response.getSdkName());
                    if (listener != null)
                        listener.onAdShow();
                }

                @Override
                public void onSplashAdClick(CSJSplashAd splashAd) {
                    FsLogger.i(TAG, "CSJSplashAd.onSplashAdClick: sourceId=" + response.getSdkName());
                    if (listener != null)
                        listener.onAdClick();
                }

                @Override
                public void onSplashAdClose(CSJSplashAd splashAd, int type) {
                    FsLogger.i(TAG, "CSJSplashAd.onSplashAdClose: type=" + type);
                    // CSJAdCloseType.CLICK_SKIP = 1: 用户点击跳过按钮
                    if (listener != null) {
                        if (type == CSJSplashCloseType.CLICK_SKIP) {
                            listener.onSplashAdSkipped();
                        } else {
                            listener.onAdClose();
                        }
                    }
                }
            });
            ad.showSplashView(container);
            FsLogger.d(TAG, "showSplash");
        } catch (Exception e) {
            FsLogger.e(TAG, "showSplash exception: " + e.getMessage(), e);
            if (listener != null)
                listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常"));
        }
    }
}
