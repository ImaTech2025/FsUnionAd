package com.ima.union.adapters.pangle;

import com.ima.union.utils.FsLogger;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTRewardVideoAd;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.RewardedVideoAdAdapter;
import com.ima.union.core.adapter.RewardedVideoAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.FsAdErrorCode;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 穿山甲激励视频广告适配器。
 */
public class PangleRewardedVideoAdapter extends PangleBaseAdapter implements RewardedVideoAdAdapter {


    private static final String TAG = "PangleRewardedVideoAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String codeId = resolveCodeId(sourceConfig);
        FsLogger.d(TAG, "▶ request[RewardedVideo]: sdkName=" + sdkName + " codeId=" + codeId
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());

        try {
            AdSlot adSlot = buildAdSlot(codeId, AdFormat.REWARDED_VIDEO, 0, 0, params);
            TTAdNative ttAdNative = createTtAdNative(context);
            if (ttAdNative == null) {
                if (callback != null)
                    callback.onLoadFailed(FsAdErrorCode.ADAPTER_CREATE_FAILED, FsAdErrorCode.buildMsg("SDK客户端创建失败", sdkName, 0, ""));
                return;
            }

            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            ttAdNative.loadRewardVideoAd(adSlot, new TTAdNative.RewardVideoAdListener() {
                @Override
                public void onError(int code, String msg) {
                    timeoutCtrl.finish();
                    if (callback != null)
                        callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, code, msg));
                }

                @Override
                public void onRewardVideoAdLoad(TTRewardVideoAd ad) {
                    FsLogger.d(TAG, "request[RewardedVideo]: ad loaded");
                }

                @Override
                public void onRewardVideoCached() {
                }

                @Override
                public void onRewardVideoCached(TTRewardVideoAd ad) {
                    double ecpm = getEcpmFromAd(ad);
                    FsLogger.d(TAG, "request[RewardedVideo]: cached ecpm=" + ecpm);
                    timeoutCtrl.finish();
                    UnionAdResponse response = buildResponse(sourceConfig, AdFormat.REWARDED_VIDEO, ad, ecpm);
                    if (callback != null) {
                        callback.onLoaded(response);
                        callback.onCachedSuccess(response);
                    }
                }
            });
        } catch (Exception e) {
            FsLogger.e(TAG, "request[RewardedVideo] exception: " + e.getMessage(), e);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
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
        TTRewardVideoAd ad = (TTRewardVideoAd) response.getNativeAd();
        if (ad == null) {
            FsLogger.e(TAG, "showRewardedVideo: no ad object");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        try {
            if (context instanceof Activity) {
                // Close guard: Pangle SDK 在用户跳过视频时会先回调 onSkippedVideo()，
                // 紧接着再回调 onAdClose()，导致外部收到两次 onAdClose。
                // 用 AtomicBoolean CAS 保证只转发第一次关闭回调。
                final AtomicBoolean closedGuard = new AtomicBoolean(false);

                ad.setRewardAdInteractionListener(new TTRewardVideoAd.RewardAdInteractionListener() {
                    @Override
                    public void onAdShow() {
                        FsLogger.d(TAG, "RewardVideo.onAdShow");
                        if (listener != null)
                            listener.onAdShow();
                    }

                    @Override
                    public void onAdVideoBarClick() {
                        FsLogger.d(TAG, "RewardVideo.onAdVideoBarClick");
                        if (listener != null)
                            listener.onAdClick();
                    }

                    @Override
                    public void onAdClose() {
                        FsLogger.d(TAG, "RewardVideo.onAdClose");
                        if (closedGuard.compareAndSet(false, true) && listener != null) {
                            listener.onAdClose();
                        }
                    }

                    @Override
                    public void onVideoComplete() {
                        if (listener != null)
                            listener.onVideoComplete();
                    }

                    @Override
                    public void onVideoError() {
                        FsLogger.e(TAG, "RewardVideo.onVideoError");
                        if (listener != null)
                            listener.onAdError(FsAdErrorCode.PLAYBACK_ERROR, FsAdErrorCode.buildMsg("视频播放错误"));
                    }

                    @Override
                    public void onRewardVerify(boolean isValid, int rewardType, String rewardName,
                                               int rewardAmount, String extra) {
                        FsLogger.d(TAG, "RewardVideo.onRewardVerify: valid=" + isValid);
                        if (listener != null)
                            listener.onRewardVerify(isValid, rewardAmount, rewardName);
                    }

                    @Override
                    public void onRewardArrived(boolean isValid, int rewardType, Bundle extra) {
                        FsLogger.d(TAG, "RewardVideo.onRewardArrived: valid=" + isValid);
                    }

                    @Override
                    public void onSkippedVideo() {
                        // 用户跳过视频，Pangle 会先回调 onSkippedVideo 再回调 onAdClose，
                        // 用 closedGuard 去重，只转发第一次关闭回调。
                        FsLogger.d(TAG, "RewardVideo.onSkippedVideo → forward as onAdClose (guarded)");
                        if (closedGuard.compareAndSet(false, true) && listener != null) {
                            listener.onAdClose();
                        }
                    }
                });
                ad.showRewardVideoAd((Activity) context);
                FsLogger.d(TAG, "showRewardedVideo");
            } else {
                if (listener != null)
                    listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("Context不是Activity"));
            }
        } catch (Exception e) {
            FsLogger.e(TAG, "showRewardedVideo exception: " + e.getMessage(), e);
            if (listener != null)
                listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常"));
        }
    }
}
