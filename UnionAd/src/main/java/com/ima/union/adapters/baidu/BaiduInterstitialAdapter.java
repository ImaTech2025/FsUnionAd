package com.ima.union.adapters.baidu;

import com.ima.union.utils.FsLogger;

import android.app.Activity;
import android.content.Context;

import com.baidu.mobads.sdk.api.ExpressInterstitialAd;
import com.baidu.mobads.sdk.api.ExpressInterstitialListener;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.InterstitialAdAdapter;
import com.ima.union.core.adapter.InterstitialAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.BidLossReason;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * 百青藤插屏广告适配器。
 */
public class BaiduInterstitialAdapter extends BaiduBaseAdapter implements InterstitialAdAdapter {


    private static final String TAG = "BaiduInterstitialAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        try {
            String adPlaceId = sourceConfig.getAdUnitId();
            ExpressInterstitialAd ad = new ExpressInterstitialAd(context, adPlaceId);
            if (sourceConfig.getBidFloor() > 0) {
                ad.setBidFloor((int) sourceConfig.getBidFloor());
            }
            ad.setLoadListener(new BaiduInterstitialCallback(sourceConfig, callback, ad));
            ad.load();
        } catch (Exception e) {
            FsLogger.e(TAG, "requestInterstitial failed: " + e.getMessage());
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sourceConfig.getSdkName(), 0, e.getMessage()));
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
        ExpressInterstitialAd ad = (ExpressInterstitialAd) response.getNativeAd();
        if (ad == null) {
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        try {
            ad.setLoadListener(new BaiduInterstitialShowHandler(response, listener));
            if (context instanceof Activity) {
                ad.show((Activity) context);
            } else {
                ad.show();
            }
            FsLogger.d(TAG, "showInterstitial");
        } catch (Exception e) {
            FsLogger.e(TAG, "showInterstitial exception: " + e.getMessage());
            if (listener != null) listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常"));
        }
    }

    // ── 加载回调 ──

    private class BaiduInterstitialCallback implements ExpressInterstitialListener {
        private final AdSourceConfig sourceConfig;
        private final AdCallback callback;
        private final ExpressInterstitialAd ad;

        BaiduInterstitialCallback(AdSourceConfig sourceConfig, AdCallback callback, ExpressInterstitialAd ad) {
            this.sourceConfig = sourceConfig;
            this.callback = callback;
            this.ad = ad;
        }

        @Override
        public void onADLoaded() {
            double ecpm = getEcpmFromAd(ad);
            Map<String, Object> extra = new HashMap<>();
            extra.put("is_bidding", true);
            if (callback != null) callback.onLoaded(buildResponse(sourceConfig, AdFormat.INTERSTITIAL, ad, ecpm, extra, readAdIsReady(ad)));
        }

        @Override
        public void onAdCacheSuccess() {
            double ecpm = getEcpmFromAd(ad);
            Map<String, Object> extra = new HashMap<>();
            extra.put("is_bidding", true);
            if (callback != null) callback.onCachedSuccess(buildResponse(sourceConfig, AdFormat.INTERSTITIAL, ad, ecpm, extra, readAdIsReady(ad)));
        }

        @Override
        public void onAdFailed(int errorCode, String message) {
            reportBidFail(ad, BidLossReason.LOAD_FAILED);
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sourceConfig.getSdkName(), -1, message));
        }

        @Override
        public void onNoAd(int errorCode, String message) {
            reportBidFail(ad, BidLossReason.NO_AD);
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sourceConfig.getSdkName(), 0, message));
        }

        @Override
        public void onAdCacheFailed() {
            reportBidFail(ad, BidLossReason.CACHE_FAILED);
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK缓存失败", sourceConfig.getSdkName(), 0, ""));
        }

        @Override
        public void onADExposed() {
        }

        @Override
        public void onAdClick() {
        }

        @Override
        public void onAdClose() {
        }

        @Override
        public void onADExposureFailed() {
        }

        @Override
        public void onLpClosed() {
        }
    }

    // ── 展示回调 ──

    private class BaiduInterstitialShowHandler implements ExpressInterstitialListener {
        private final UnionAdResponse response;
        private final InterstitialAdListener listener;

        BaiduInterstitialShowHandler(UnionAdResponse response, InterstitialAdListener listener) {
            this.response = response;
            this.listener = listener;
        }

        @Override
        public void onADLoaded() {
            // load 阶段已由 AdCallback 处理，此处不需再通知
        }

        @Override
        public void onADExposed() {
            if (listener != null) listener.onAdShow();
        }

        @Override
        public void onAdClick() {
            if (listener != null) listener.onAdClick();
        }

        @Override
        public void onAdClose() {
            if (listener != null) listener.onAdClose();
        }

        @Override
        public void onLpClosed() {
            FsLogger.d(TAG, "BaiduInterstitial.onLpClosed (ignored, onAdClose handles close)");
        }

        @Override
        public void onAdFailed(int errorCode, String message) {
            if (listener != null) listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), errorCode, message));
        }

        @Override
        public void onNoAd(int errorCode, String message) {
            if (listener != null) listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), errorCode, message));
        }

        @Override
        public void onADExposureFailed() {
            if (listener != null) listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), 0, ""));
        }

        @Override
        public void onAdCacheSuccess() {
        }

        @Override
        public void onAdCacheFailed() {
        }
    }
}
