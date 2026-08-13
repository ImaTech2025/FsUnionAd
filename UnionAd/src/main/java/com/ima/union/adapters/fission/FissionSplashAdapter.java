package com.ima.union.adapters.fission;
import com.ima.union.utils.FsLogger;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.SplashAdAdapter;
import com.ima.union.core.adapter.SplashAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.BidLossReason;
import com.ima.union.core.model.FsAdErrorCode;

import com.zm.fissionsdk.api.FissionSdk;
import com.zm.fissionsdk.api.FissionSlot;
import com.zm.fissionsdk.api.interfaces.IFissionLoadManager;
import com.zm.fissionsdk.api.interfaces.IFissionSplash;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 飞梭开屏广告适配器。
 */
public class FissionSplashAdapter extends FissionBaseAdapter implements SplashAdAdapter {


    private static final String TAG = "FissionSplashAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        FsLogger.d(TAG, "▶ request[Splash]: sdkName=" + sdkName + " slotId=" + sourceConfig.getAdUnitId()
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());
        logRequestedSizeIfPresent("request[Splash]", params);

        try {
            FissionSlot slot = buildSlot(context, sourceConfig, params.getVideoMuted());
            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();
            AtomicReference<UnionAdResponse> responseRef = new AtomicReference<>();
            FissionSdk.getLoadManager().loadSplash(slot, new IFissionLoadManager.AsyncSplashLoadListener() {
                @Override
                public void onLoad(List<IFissionSplash> list) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    if (list != null && !list.isEmpty()) {
                        IFissionSplash ad = list.get(0);
                        double ecpm = ad.getECpm();
                        FsLogger.d(TAG, "request[Splash]: ecpm=" + ecpm);
                        UnionAdResponse response = buildResponse(sourceConfig, AdFormat.SPLASH, ad, ecpm);
                        responseRef.set(response);
                        if (callback != null) callback.onLoaded(response);
                    } else {
                        FsLogger.w(TAG, "request[Splash]: no fill");
                        if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sdkName, 0, ""));
                    }
                }

                @Override
                public void onError(int code, String msg) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    FsLogger.e(TAG, "request[Splash]: code=" + code + " msg=" + msg);
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
        FsLogger.d(TAG, "▶ showSplash: sourceId=" + response.getSdkName() + ", container="
                + (container != null ? container.getClass().getSimpleName() : "null"));
        if (container == null) {
            FsLogger.e(TAG, "showSplash: container is null");
            if (listener != null) listener.onAdError(FsAdErrorCode.CONTAINER_NULL, FsAdErrorCode.buildMsg("展示容器为空"));
            return;
        }
        if (!(response.getNativeAd() instanceof IFissionSplash)) {
            FsLogger.e(TAG, "showSplash: nativeAd is not IFissionSplash");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        IFissionSplash splash = (IFissionSplash) response.getNativeAd();

        splash.setSplashInteractionListener(new IFissionSplash.SplashInteractionListener() {
            @Override public void onShow() {
                FsLogger.i(TAG, "IFissionSplash.onShow: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdShow();
            }
            @Override public void onShowFailed(int code, String msg) {
                FsLogger.e(TAG, "IFissionSplash.onShowFailed: code=" + code + " msg=" + msg);
                if (listener != null) listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), code, msg));
            }
            @Override public void onPresent() {
                FsLogger.d(TAG, "IFissionSplash.onPresent");
            }
            @Override public void onClick(View view) {
                FsLogger.i(TAG, "IFissionSplash.onClick: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdClick();
            }
            @Override public void onSkip() {
                FsLogger.i(TAG, "IFissionSplash.onSkip: sourceId=" + response.getSdkName());
                if (listener != null) listener.onSplashAdSkipped();
            }
            @Override public void onClose() {
                FsLogger.i(TAG, "IFissionSplash.onClose: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdClose();
            }
        });

        container.removeAllViews();
        splash.showSplash(container);
        FsLogger.d(TAG, "showSplash");
    }
}
