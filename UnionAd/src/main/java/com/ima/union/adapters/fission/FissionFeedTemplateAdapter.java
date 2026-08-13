package com.ima.union.adapters.fission;

import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.View;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.FeedAdAdapter;
import com.ima.union.core.adapter.FeedAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.FsAdErrorCode;

import com.zm.fissionsdk.api.FissionSdk;
import com.zm.fissionsdk.api.FissionSlot;
import com.zm.fissionsdk.api.interfaces.IFissionLoadManager;
import com.zm.fissionsdk.api.interfaces.IFissionNative;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞梭信息流模板广告适配器。
 *
 * <p>模板广告由 Fission SDK 内部完成渲染，返回可直接展示的 View。</p>
 */
public class FissionFeedTemplateAdapter extends FissionBaseAdapter implements FeedAdAdapter {


    private static final String TAG = "FissionFeedTemplateAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        FsLogger.d(TAG, "▶ request[FeedTemplate]: sdkName=" + sdkName + " slotId=" + sourceConfig.getAdUnitId()
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());
        logRequestedSizeIfPresent("request[FeedTemplate]", params);


        try {
            FissionSlot slot = buildSlot(context, sourceConfig, params.getVideoMuted());
            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            FissionSdk.getLoadManager().loadNative(slot, new IFissionLoadManager.AsyncNativeLoadListener() {
                @Override
                public void onLoad(List<IFissionNative> list) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    if (list != null && !list.isEmpty()) {
                        IFissionNative ad = list.get(0);
                        double ecpm = ad.getECpm();
                        FsLogger.d(TAG, "request[FeedTemplate]: ecpm=" + ecpm);
                        Map<String, Object> extra = new HashMap<>();
                        extra.put("is_template", true);
                        UnionAdResponse response = new UnionAdResponse.Builder()
                                .sdkName(sourceConfig.getSdkName())
                                .adUnitId(sourceConfig.getAdUnitId())
                                .sdkType(com.ima.union.core.model.AdSdkType.FISSION)
                                .adFormat(AdFormat.FEED_TEMPLATE)
                                .ecpm(ecpm)
                                .expireTimeMs(System.currentTimeMillis() + sourceConfig.getExpireTimeMs())
                                .nativeAd(ad)
                                .extra(extra)
                                .build();
                        if (callback != null) callback.onLoaded(response);
                    } else {
                        FsLogger.w(TAG, "request[FeedTemplate]: no fill");
                        if (callback != null)
                            callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sdkName, 0, ""));
                    }
                }

                @Override
                public void onError(int code, String msg) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    FsLogger.e(TAG, "request[FeedTemplate]: code=" + code + " msg=" + msg);
                    if (callback != null)
                        callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, code, msg));
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
        if (!(response.getNativeAd() instanceof IFissionNative)) {
            FsLogger.e(TAG, "renderFeedAd[Template]: nativeAd is not IFissionNative");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        IFissionNative nativeAd = (IFissionNative) response.getNativeAd();
        View expressView = nativeAd.getExpressView(context);
        if (expressView == null) {
            FsLogger.e(TAG, "renderFeedAd[Template]: getExpressView() returned null");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }

        // 模板广告同步获取 View，通知宿主渲染完成
        if (listener != null) listener.onFeedAdRendered();

        nativeAd.setNativeExpressListener(new IFissionNative.NativeExpressInteractionListener() {
            @Override
            public void onShow() {
                FsLogger.d(TAG, "IFissionNative.onShow: sourceId=" + response.getSdkName());
                if (listener != null)
                    listener.onAdShow();
            }

            @Override
            public void onShowFailed(int code, String msg) {
                FsLogger.e(TAG, "IFissionNative.onShowFailed: code=" + code + " msg=" + msg);
                if (listener != null)
                    listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), code, msg));
            }

            @Override
            public void onClick(View view) {
                FsLogger.d(TAG, "IFissionNative.onClick: sourceId=" + response.getSdkName());
                if (listener != null)
                    listener.onAdClick();
            }

            @Override
            public void onCreativeClick(View view) {
                FsLogger.d(TAG, "IFissionNative.onCreativeClick: sourceId=" + response.getSdkName());
            }

            @Override
            public void onClose() {
                FsLogger.d(TAG, "IFissionNative.onClose: sourceId=" + response.getSdkName());
                if (listener != null)
                    listener.onAdClose();
            }
        });

        FsLogger.d(TAG, "renderFeedAd[Template]");
        return expressView;
    }
}
