package com.ima.union.adapters.fission;

import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.View;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.RewardedVideoAdAdapter;
import com.ima.union.core.adapter.RewardedVideoAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.BidLossReason;
import com.ima.union.core.model.FsAdErrorCode;

import com.zm.fissionsdk.api.FissionSdk;
import com.zm.fissionsdk.api.FissionSlot;
import com.zm.fissionsdk.api.interfaces.IFissionLoadManager;
import com.zm.fissionsdk.api.interfaces.IFissionRewardVideo;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 飞梭激励视频广告适配器。
 */
public class FissionRewardedVideoAdapter extends FissionBaseAdapter implements RewardedVideoAdAdapter {


    private static final String TAG = "FissionRewardedVideoAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        FsLogger.d(TAG, "▶ request[RewardedVideo]: sdkName=" + sdkName + " slotId=" + sourceConfig.getAdUnitId()
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());
        logRequestedSizeIfPresent("request[RewardedVideo]", params);


        try {
            FissionSlot slot = buildSlot(context, sourceConfig, params.getVideoMuted());
            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            AtomicReference<UnionAdResponse> responseRef = new AtomicReference<>();
            FissionSdk.getLoadManager().loadRewardVideo(slot, new IFissionLoadManager.AsyncRewardVideoLoadListener() {
                @Override
                public void onLoad(List<IFissionRewardVideo> list) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    if (list != null && !list.isEmpty()) {
                        IFissionRewardVideo ad = list.get(0);
                        double ecpm = ad.getECpm();
                        FsLogger.d(TAG, "request[RewardedVideo]: ecpm=" + ecpm);
                        UnionAdResponse response = buildResponse(sourceConfig, AdFormat.REWARDED_VIDEO, ad, ecpm);
                        responseRef.set(response);
                        if (callback != null) callback.onLoaded(response);
                    } else {
                        FsLogger.w(TAG, "request[RewardedVideo]: no fill");
                        if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sdkName, 0, ""));
                    }
                }

                @Override
                public void onError(int code, String msg) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    FsLogger.e(TAG, "request[RewardedVideo]: code=" + code + " msg=" + msg);
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
            FsLogger.e(TAG, "request[RewardedVideo] exception: " + e.getMessage(), e);
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
        }
    }

    @Override
    public void showRewardedVideo(Context context, UnionAdResponse response, RewardedVideoAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "showRewardedVideo: response is null");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        FsLogger.d(TAG, "▶ showRewardedVideo: sourceId=" + response.getSdkName());
        if (!(response.getNativeAd() instanceof IFissionRewardVideo)) {
            FsLogger.e(TAG, "showRewardedVideo: nativeAd is not IFissionRewardVideo");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        IFissionRewardVideo rewarded = (IFissionRewardVideo) response.getNativeAd();

        rewarded.setRewardInteractionListener(new IFissionRewardVideo.RewardVideoInteractionListener() {
            @Override
            public void onShow() {
                FsLogger.i(TAG, "IFissionRewardVideo.onShow: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdShow();
            }

            @Override
            public void onShowFailed(int code, String msg) {
                FsLogger.e(TAG, "IFissionRewardVideo.onShowFailed: code=" + code + " msg=" + msg);
                if (listener != null) listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), code, msg));
            }

            @Override
            public void onClick(View view) {
                FsLogger.i(TAG, "IFissionRewardVideo.onClick: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdClick();
            }

            @Override
            public void onVideoComplete() {
                if (listener != null) listener.onVideoComplete();
            }

            @Override
            public void onVideoError() {
                FsLogger.e(TAG, "IFissionRewardVideo.onVideoError: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdError(FsAdErrorCode.PLAYBACK_ERROR, FsAdErrorCode.buildMsg("视频播放错误"));
            }

            @Override
            public void onRewardVerify(boolean isReward, int amount, android.os.Bundle bundle) {
                String rewardName = bundle.getString("reward_name", "");
                FsLogger.i(TAG, "IFissionRewardVideo.onRewardVerify: isReward=" + isReward + " amount=" + amount + " name=" + rewardName);
                if (listener != null) listener.onRewardVerify(isReward, amount, rewardName);
            }

            @Override
            public void onClose() {
                FsLogger.i(TAG, "IFissionRewardVideo.onClose: sourceId=" + response.getSdkName());
                if (listener != null) listener.onAdClose();
            }
        });

        rewarded.showReward(context);
        FsLogger.d(TAG, "showRewardedVideo");
    }
}
