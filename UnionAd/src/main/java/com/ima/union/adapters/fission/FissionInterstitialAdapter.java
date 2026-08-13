package com.ima.union.adapters.fission;

import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.View;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.InterstitialAdAdapter;
import com.ima.union.core.adapter.InterstitialAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.BidLossReason;
import com.ima.union.core.model.FsAdErrorCode;

import com.zm.fissionsdk.api.FissionSdk;
import com.zm.fissionsdk.api.FissionSlot;
import com.zm.fissionsdk.api.interfaces.IFissionInterstitial;
import com.zm.fissionsdk.api.interfaces.IFissionLoadManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 飞梭插屏广告适配器。
 */
public class FissionInterstitialAdapter extends FissionBaseAdapter implements InterstitialAdAdapter {


    private static final String TAG = "FissionInterstitialAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        FsLogger.d(TAG, "▶ request[Interstitial]: sdkName=" + sdkName + " slotId=" + sourceConfig.getAdUnitId()
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());
        logRequestedSizeIfPresent("request[Interstitial]", params);

        try {
            FissionSlot slot = buildSlot(context, sourceConfig, params.getVideoMuted());
            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();
            AtomicReference<UnionAdResponse> responseRef = new AtomicReference<>();
            FissionSdk.getLoadManager().loadInterstitial(slot, new IFissionLoadManager.AsyncInterstitialLoadListener() {
                @Override
                public void onLoad(List<IFissionInterstitial> list) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    if (list != null && !list.isEmpty()) {
                        IFissionInterstitial ad = list.get(0);
                        double ecpm = ad.getECpm();
                        FsLogger.d(TAG, "request[Interstitial]: ecpm=" + ecpm);
                        UnionAdResponse response = buildResponse(sourceConfig, AdFormat.INTERSTITIAL, ad, ecpm);
                        responseRef.set(response);
                        if (callback != null) callback.onLoaded(response);
                    } else {
                        FsLogger.w(TAG, "request[Interstitial]: no fill");
                        if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sdkName, 0, ""));
                    }
                }

                @Override
                public void onError(int code, String msg) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    FsLogger.e(TAG, "request[Interstitial]: code=" + code + " msg=" + msg);
                    if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, code, msg));
                }

                @Override
                public void onMaterialCached() {
                    UnionAdResponse response = responseRef.get();
                    if (response != null) {
                        if (callback != null) callback.onCachedSuccess(response);
                    }
                }

                @Override
                public void onMaterialCacheFailed(int code, String msg) {
                    UnionAdResponse response = responseRef.get();
                    if (response != null) {
                        reportBidFail(response.getNativeAd(), BidLossReason.CACHE_FAILED);
                    }
                }
            });
        } catch (Exception e) {
            FsLogger.e(TAG, "request[Interstitial] exception: " + e.getMessage(), e);
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
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
        if (!(response.getNativeAd() instanceof IFissionInterstitial)) {
            FsLogger.e(TAG, "showInterstitial: nativeAd is not IFissionInterstitial");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        IFissionInterstitial interstitial = (IFissionInterstitial) response.getNativeAd();

        interstitial.setInterstitialInteractionListener(new IFissionInterstitial.InterstitialInteractionListener() {
            @Override
            public void onShow() {
                FsLogger.i(TAG, "IFissionInterstitial.onShow: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdShow();
            }

            @Override
            public void onShowFailed(int code, String msg) {
                FsLogger.e(TAG, "IFissionInterstitial.onShowFailed: code=" + code + " msg=" + msg);
                if (listener != null) listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), code, msg));
            }

            @Override
            public void onClick(View view) {
                FsLogger.i(TAG, "IFissionInterstitial.onClick: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdClick();
            }

            @Override
            public void onClose() {
                FsLogger.i(TAG, "IFissionInterstitial.onClose: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdClose();
            }
        });

        interstitial.showInterstitial(context);
        FsLogger.d(TAG, "showInterstitial");
    }
}
