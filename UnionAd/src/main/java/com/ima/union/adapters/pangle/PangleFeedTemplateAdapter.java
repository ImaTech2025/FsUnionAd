package com.ima.union.adapters.pangle;

import com.bytedance.sdk.openadsdk.TTNativeExpressAd;
import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.View;

import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.FeedAdAdapter;
import com.ima.union.core.adapter.FeedAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.FsAdErrorCode;

import java.util.List;

/**
 * 穿山甲信息流模板广告适配器。
 *
 * <p>信息流模板广告由 Pangle SDK 内部完成渲染，返回可直接展示的 View。</p>
 */
public class PangleFeedTemplateAdapter extends PangleBaseAdapter implements FeedAdAdapter {


    private static final String TAG = "PangleFeedTemplateAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String codeId = resolveCodeId(sourceConfig);
        FsLogger.d(TAG, "▶ request[FeedTemplate]: sdkName=" + sdkName + " codeId=" + codeId + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());

        try {
            AdSlot adSlot = buildAdSlot(codeId, AdFormat.FEED_TEMPLATE, 0, 0, params);
            TTAdNative ttAdNative = createTtAdNative(context);
            if (ttAdNative == null) {
                if (callback != null)
                    callback.onLoadFailed(FsAdErrorCode.ADAPTER_CREATE_FAILED, FsAdErrorCode.buildMsg("SDK客户端创建失败", sdkName, 0, ""));
                return;
            }

            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();
            ttAdNative.loadNativeExpressAd(adSlot, new TTAdNative.NativeExpressAdListener() {
                @Override
                public void onError(int code, String msg) {
                    timeoutCtrl.finish();
                    if (callback != null)
                        callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, code, msg));
                }

                @Override
                public void onNativeExpressAdLoad(List<TTNativeExpressAd> list) {
                    timeoutCtrl.finish();
                    if (list != null && !list.isEmpty()) {
                        TTNativeExpressAd ad = list.get(0);
                        double ecpm = getEcpmFromAd(ad);
                        FsLogger.d(TAG, "request[FeedTemplate]: ecpm=" + ecpm);
                        UnionAdResponse response = buildResponse(sourceConfig, AdFormat.FEED_TEMPLATE, ad, ecpm);
                        if (callback != null) callback.onLoaded(response);
                    } else {
                        if (callback != null) {
                            callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sdkName, 0, ""));
                        }
                    }
                }
            });
        } catch (Exception e) {
            FsLogger.e(TAG, "request[FeedTemplate] exception: " + e.getMessage(), e);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
        }
    }

    @Override
    public View renderFeedAd(Context context, UnionAdResponse response, FeedAdListener listener) {
        FsLogger.d(TAG, "▶ renderFeedAd[Template]: sourceId=" + response.getSdkName());
        TTNativeExpressAd ad = (TTNativeExpressAd) response.getNativeAd();
        if (ad == null) {
            FsLogger.e(TAG, "renderFeedAd[Template]: no ad object");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        try {
            ad.setExpressInteractionListener(new TTNativeExpressAd.ExpressAdInteractionListener() {
                @Override
                public void onAdClicked(View view, int i) {
                    FsLogger.d(TAG, "Template.onAdClicked: sourceId=" + response.getSdkName());
                    if (listener != null) listener.onAdClick();
                }

                @Override
                public void onAdShow(View view, int i) {
                    FsLogger.d(TAG, "Template.onAdShow: sourceId=" + response.getSdkName());
                    if (listener != null) listener.onAdShow();
                }

                @Override
                public void onRenderFail(View view, String s, int i) {
                    FsLogger.e(TAG, "Template.onRenderFail: code=" + i + " msg=" + s + " sourceId=" + response.getSdkName());
                    if (listener != null) listener.onAdError(FsAdErrorCode.RENDER_FAILED,
                            FsAdErrorCode.buildMsg("模板渲染失败: " + s, response.getSdkName(), i, s));
                }

                @Override
                public void onRenderSuccess(View view, float v, float v1) {
                    FsLogger.d(TAG, "Template.onRenderSuccess: width=" + v + " height=" + v1 + " sourceId=" + response.getSdkName());
                    if (listener != null) listener.onFeedAdRendered();
                }
            });
            ad.render();
            FsLogger.d(TAG, "renderFeedAd[Template]");
            return ad.getExpressAdView();
        } catch (Exception e) {
            FsLogger.e(TAG, "renderFeedAd[Template] exception: " + e.getMessage(), e);
            if (listener != null)
                listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常"));
            return null;
        }
    }
}
