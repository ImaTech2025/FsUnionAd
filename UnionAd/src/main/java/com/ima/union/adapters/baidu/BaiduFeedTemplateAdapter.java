package com.ima.union.adapters.baidu;

import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.View;

import com.baidu.mobads.sdk.api.BaiduNativeManager;
import com.baidu.mobads.sdk.api.ExpressResponse;
import com.baidu.mobads.sdk.api.RequestParameters;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.FeedAdAdapter;
import com.ima.union.core.adapter.FeedAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.BidLossReason;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 百青藤信息流模板广告适配器。
 *
 * <p>通过 {@link BaiduNativeManager#loadExpressAd} 加载模板渲染广告，
 * 渲染阶段调用 {@link ExpressResponse#render()} 获取模板 View。</p>
 */
public class BaiduFeedTemplateAdapter extends BaiduBaseAdapter implements FeedAdAdapter {


    private static final String TAG = "BaiduFeedTemplateAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        try {
            String adPlaceId = sourceConfig.getAdUnitId();
            BaiduNativeManager manager = new BaiduNativeManager(context, adPlaceId);
            if (sourceConfig.getBidFloor() > 0) {
                manager.setBidFloor((int) sourceConfig.getBidFloor());
            }
            RequestParameters requestParams = new RequestParameters.Builder().build();
            manager.loadExpressAd(requestParams, new BaiduFeedTemplateLoadCallback(sourceConfig, callback));
        } catch (Exception e) {
            FsLogger.e(TAG, "requestFeedTemplate failed: " + e.getMessage());
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sourceConfig.getSdkName(), 0, e.getMessage()));
        }
    }

    @Override
    public View renderFeedAd(Context context, UnionAdResponse response, FeedAdListener listener) {
        FsLogger.d(TAG, "\u25b6 renderFeedAd: sourceId=" + response.getSdkName());
        ExpressResponse expressResponse = (ExpressResponse) response.getNativeAd();
        if (expressResponse == null) {
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        try {
            expressResponse.setInteractionListener(new BaiduFeedTemplateShowHandler(response, listener));
            expressResponse.render();
            View adView = expressResponse.getExpressAdView();
            // onFeedAdRendered 由 onAdRenderSuccess 异步回调，此处不重复调用
            FsLogger.d(TAG, "renderFeedAd adView=" + (adView != null ? "non-null" : "null"));
            return adView;
        } catch (Exception e) {
            FsLogger.e(TAG, "renderFeedAd exception: " + e.getMessage());
            if (listener != null)
                listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常"));
            return null;
        }
    }

    // ── 加载回调 ──

    private class BaiduFeedTemplateLoadCallback implements BaiduNativeManager.ExpressAdListener {
        private final AdSourceConfig sourceConfig;
        private final AdCallback callback;
        private ExpressResponse lastExpressResponse;

        BaiduFeedTemplateLoadCallback(AdSourceConfig sourceConfig, AdCallback callback) {
            this.sourceConfig = sourceConfig;
            this.callback = callback;
        }

        @Override
        public void onNativeLoad(List<ExpressResponse> expressResponses) {
            if (expressResponses != null && !expressResponses.isEmpty()) {
                lastExpressResponse = expressResponses.get(0);
                double ecpm = getEcpmFromAd(lastExpressResponse);
                FsLogger.d(TAG, "requestFeedTemplate: onNativeLoad, sourceId=" + sourceConfig.getSdkName()
                        + " ecpm=" + ecpm);
                Map<String, Object> extra = new HashMap<>();
                extra.put("is_bidding", true);
                if (callback != null)
                    callback.onLoaded(buildResponse(sourceConfig, AdFormat.FEED_TEMPLATE,
                            lastExpressResponse, ecpm, extra, readAdIsReady(lastExpressResponse)));
            } else {
                FsLogger.w(TAG, "requestFeedTemplate: onNativeLoad empty list");
                if (callback != null)
                    callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sourceConfig.getSdkName(), 0, ""));
            }
        }

        @Override
        public void onNativeFail(int errorCode, String message, ExpressResponse expressResponse) {
            FsLogger.e(TAG, "requestFeedTemplate: onNativeFail, sourceId=" + sourceConfig.getSdkName()
                    + " code=" + errorCode + " msg=" + message);
            if (expressResponse != null) reportBidFail(expressResponse, BidLossReason.LOAD_FAILED);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sourceConfig.getSdkName(), -1, message));
        }

        @Override
        public void onNoAd(int code, String msg, ExpressResponse expressResponse) {
            FsLogger.w(TAG, "requestFeedTemplate: onNoAd, sourceId=" + sourceConfig.getSdkName()
                    + " code=" + code + " msg=" + msg);
            if (expressResponse != null) reportBidFail(expressResponse, BidLossReason.NO_AD);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sourceConfig.getSdkName(), 0, msg));
        }

        @Override
        public void onVideoDownloadSuccess() {
        }

        @Override
        public void onVideoDownloadFailed() {
        }

        @Override
        public void onLpClosed() {
        }
    }

    // ── 展示回调 ──

    private class BaiduFeedTemplateShowHandler implements ExpressResponse.ExpressInteractionListener {
        private final UnionAdResponse response;
        private final FeedAdListener listener;

        BaiduFeedTemplateShowHandler(UnionAdResponse response, FeedAdListener listener) {
            this.response = response;
            this.listener = listener;
        }

        @Override
        public void onAdRenderSuccess(View adView, float width, float height) {
            if (listener != null) listener.onFeedAdRendered();
        }

        @Override
        public void onAdExposed() {
            if (listener != null) listener.onAdShow();
        }

        @Override
        public void onAdClick() {
            if (listener != null) listener.onAdClick();
        }

        @Override
        public void onAdRenderFail(View adView, String reason, int code) {
            if (listener != null)
                listener.onAdError(FsAdErrorCode.RENDER_FAILED, FsAdErrorCode.buildMsg("模板渲染失败"));
        }

        @Override
        public void onAdUnionClick() {
        }
    }
}
