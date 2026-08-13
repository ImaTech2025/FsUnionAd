package com.ima.union.adapters.baidu;

import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.ViewGroup;

import com.baidu.mobads.sdk.api.RequestParameters;
import com.baidu.mobads.sdk.api.SplashAd;
import com.baidu.mobads.sdk.api.SplashInteractionListener;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.FsUnionListenerBridge;
import com.ima.union.core.adapter.SplashAdAdapter;
import com.ima.union.core.adapter.SplashAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.BidLossReason;

import java.util.HashMap;
import java.util.Map;

/**
 * 百青藤开屏广告适配器。
 */
public class BaiduSplashAdapter extends BaiduBaseAdapter implements SplashAdAdapter {

    private static final String TAG = "BaiduSplashAdapter";


    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        try {
            String adPlaceId = sourceConfig.getAdUnitId();
            RequestParameters requestParams = new RequestParameters.Builder()
                    .addExtra(SplashAd.KEY_TIMEOUT, String.valueOf(sourceConfig.getTimeout()))
                    .build();

            // 桥接器：请求阶段注入 SDK 回调，展示阶段绑定 SplashAdListener
            FsUnionListenerBridge<SplashAdListener> showBridge = new FsUnionListenerBridge<>();

            BaiduSplashCallback splashCallback = new BaiduSplashCallback(sourceConfig, callback, context, showBridge);
            SplashAd ad = new SplashAd(context, adPlaceId, requestParams, splashCallback);
            splashCallback.bindAd(ad);

            if (sourceConfig.getBidFloor() > 0) {
                ad.setBidFloor((int) sourceConfig.getBidFloor());
            }
            ad.load();
        } catch (Exception e) {
            FsLogger.e(TAG, "requestSplash failed: " + e.getMessage());
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sourceConfig.getSdkName(), 0, e.getMessage()));
        }
    }

    @Override
    public void showSplash(Context context, UnionAdResponse response, ViewGroup container, SplashAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "showSplash: response is null");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        FsLogger.d(TAG, "▶ showSplash: sourceId=" + response.getSdkName());
        if (container == null) {
            if (listener != null) listener.onAdError(FsAdErrorCode.CONTAINER_NULL, FsAdErrorCode.buildMsg("展示容器为空"));
            return;
        }
        SplashAd ad = (SplashAd) response.getNativeAd();
        if (ad == null) {
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
            container.removeAllViews();
            ad.show(container);
            FsLogger.d(TAG, "showSplash");
        } catch (Exception e) {
            FsLogger.e(TAG, "showSplash exception: " + e.getMessage());
            if (listener != null) listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  内部 Handler：统一加载+运行时回调
    // ════════════════════════════════════════════════════════════════

    private class BaiduSplashCallback implements SplashInteractionListener {
        private final AdSourceConfig sourceConfig;
        private final AdCallback callback;
        private final FsUnionListenerBridge<SplashAdListener> showBridge;
        private SplashAd ad;

        BaiduSplashCallback(AdSourceConfig sourceConfig, AdCallback callback, Context context, FsUnionListenerBridge<SplashAdListener> showBridge) {
            this.sourceConfig = sourceConfig;
            this.callback = callback;
            this.showBridge = showBridge;
        }

        void bindAd(SplashAd ad) {
            this.ad = ad;
        }

        @Override
        public void onADLoaded() {
            if (ad != null) {
                double ecpm = getEcpmFromAd(ad);
                FsLogger.d(TAG, "requestSplash: onADLoaded ecpm=" + ecpm);
                Map<String, Object> extra = new HashMap<>();
                extra.put("is_bidding", true);
                extra.put(FsUnionListenerBridge.EXTRA_KEY, showBridge);
                if (callback != null) callback.onLoaded(buildResponse(sourceConfig, AdFormat.SPLASH, ad, ecpm, extra, readAdIsReady(ad)));
            }
        }

        @Override
        public void onAdCacheSuccess() {
            if (ad != null) {
                double ecpm = getEcpmFromAd(ad);
                Map<String, Object> extra = new HashMap<>();
                extra.put("is_bidding", true);
                extra.put(FsUnionListenerBridge.EXTRA_KEY, showBridge);
                if (callback != null) callback.onCachedSuccess(buildResponse(sourceConfig, AdFormat.SPLASH, ad, ecpm, extra, readAdIsReady(ad)));
            }
        }

        @Override
        public void onAdFailed(String reason) {
            reportBidFail(ad, BidLossReason.LOAD_FAILED);
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sourceConfig.getSdkName(), -1, reason));
        }

        @Override
        public void onAdCacheFailed() {
            reportBidFail(ad, BidLossReason.CACHE_FAILED);
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK缓存失败", sourceConfig.getSdkName(), 0, ""));
        }

        @Override
        public void onAdPresent() {
            FsLogger.i(TAG, "BaiduSplash.onAdPresent: forwarding to show listener");
//            SplashAdListener l = showBridge.get(); if (l != null) l.onAdShow();
        }

        @Override
        public void onAdExposed() {
            FsLogger.i(TAG, "BaiduSplash.onAdExposed: forwarding to show listener");
            SplashAdListener l = showBridge.get();
            if (l != null) l.onAdShow();
        }

        @Override
        public void onAdClick() {
            FsLogger.i(TAG, "BaiduSplash.onAdClick: forwarding to show listener");
            SplashAdListener l = showBridge.get();
            if (l != null) l.onAdClick();
        }

        @Override
        public void onAdSkip() {
            FsLogger.i(TAG, "BaiduSplash.onAdSkip: forwarding to show listener");
            SplashAdListener l = showBridge.get();
            if (l != null)
                l.onSplashAdSkipped();
        }

        @Override
        public void onAdDismissed() {
            FsLogger.i(TAG, "BaiduSplash.onAdDismissed: forwarding to show listener");
            SplashAdListener l = showBridge.get();
            if (l != null) {
                l.onAdClose();
                showBridge.clear();
            }
        }

        @Override
        public void onLpClosed() {
            FsLogger.d(TAG, "BaiduSplash.onLpClosed (ignored, onAdDismissed handles close)");
        }
    }
}
