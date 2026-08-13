package com.ima.union.adapters.gdt;
import com.ima.union.utils.FsLogger;
import android.content.Context;
import android.os.Build;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.FsUnionListenerBridge;
import com.ima.union.core.adapter.RewardedVideoAdAdapter;
import com.ima.union.core.adapter.RewardedVideoAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.qq.e.ads.rewardvideo.RewardVideoAD;
import com.qq.e.ads.rewardvideo.RewardVideoADListener;
import com.qq.e.comm.util.AdError;

import java.util.Map;

/**
 * 优量汇激励视频广告适配器。
 */
public class GdtRewardedVideoAdapter extends GdtBaseAdapter implements RewardedVideoAdAdapter {

    private static final String TAG = "GdtRewardedVideoAdapter";


    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String placeId = resolvePlaceId(sourceConfig);
        FsLogger.d(TAG, "▶ request[RewardedVideo]: sdkName=" + sdkName + " placeId=" + placeId
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());

        try {
            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            // 桥接器：请求阶段注入 SDK 回调，展示阶段绑定 RewardedVideoAdListener
            FsUnionListenerBridge<RewardedVideoAdListener> showBridge = new FsUnionListenerBridge<>();

            final RewardVideoAD[] adHolder = new RewardVideoAD[1];
            adHolder[0] = new RewardVideoAD(context, placeId, new RewardVideoADListener() {
                @Override
                public void onADLoad() {
                    double ecpm = 0;
                    try {
                        ecpm = adHolder[0].getECPM();
                    } catch (Exception ignored) {}
                    FsLogger.d(TAG, "request[RewardedVideo]: onADLoad ecpm=" + ecpm);
                    timeoutCtrl.finish();
                    java.util.Map<String, Object> extra = new java.util.HashMap<>();
                    extra.put(FsUnionListenerBridge.EXTRA_KEY, showBridge);
                    UnionAdResponse response = buildResponse(sourceConfig, AdFormat.REWARDED_VIDEO, adHolder[0], (int) ecpm, extra);
                    if (callback != null) {
                        callback.onLoaded(response);
                        callback.onCachedSuccess(response);
                    }
                }

                @Override
                public void onError(AdError error) {
                    timeoutCtrl.finish();
                    if (callback != null) callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, error.getErrorCode(), error.getErrorMsg()));
                }

                @Override public void onVideoCached() {
                    FsLogger.d(TAG, "request[RewardedVideo]: onVideoCached");
                }

                @Override public void onADShow() {
                    FsLogger.i(TAG, "GdtRewardedVideo.onADShow: sdkName=" + sdkName);
                    RewardedVideoAdListener l = showBridge.get();
                    if (l != null) {
                        l.onAdShow();
                    }
                }
                @Override public void onADExpose() {
                    FsLogger.d(TAG, "GdtRewardedVideo.onADExpose: sdkName=" + sdkName);
                }
                @Override public void onReward(Map<String, Object> reward) {
                    FsLogger.i(TAG, "GdtRewardedVideo.onReward: sdkName=" + sdkName + " reward=" + reward);
                    RewardedVideoAdListener l = showBridge.get();
                    if (l != null) {
                        l.onRewardVerify(true, 0, "");
                    }
                }
                @Override public void onADClick() {
                    FsLogger.i(TAG, "GdtRewardedVideo.onADClick: sdkName=" + sdkName);
                    RewardedVideoAdListener l = showBridge.get();
                    if (l != null) {
                        l.onAdClick();
                    }
                }
                @Override public void onVideoComplete() {
                    FsLogger.d(TAG, "request[RewardedVideo]: onVideoComplete");
                    RewardedVideoAdListener l = showBridge.get();
                    if (l != null) {
                        l.onVideoComplete();
                    }
                }
                @Override public void onADClose() {
                    FsLogger.i(TAG, "GdtRewardedVideo.onADClose: sdkName=" + sdkName);
                    RewardedVideoAdListener l = showBridge.get();
                    if (l != null) {
                        l.onAdClose();
                        showBridge.clear();
                    }
                }
            }, params.getVideoMuted());
            adHolder[0].loadAD();
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
        RewardVideoAD ad = (RewardVideoAD) response.getNativeAd();
        if (ad == null) {
            FsLogger.e(TAG, "showRewardedVideo: no ad object");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        try {
            // 从 extra 取出桥接器并绑定当前 listener，使请求阶段注册的 SDK 回调能转发事件
            java.util.Map<String, Object> extra = response.getExtra();
            if (extra != null) {
                FsUnionListenerBridge<RewardedVideoAdListener> showBridge = (FsUnionListenerBridge<RewardedVideoAdListener>) extra.get(FsUnionListenerBridge.EXTRA_KEY);
                if (showBridge != null) showBridge.bind(listener);
            }
            ad.showAD();
            FsLogger.d(TAG, "showRewardedVideo (show/click/expose callbacks forwarded via mShowListener)");
        } catch (Exception e) {
            FsLogger.e(TAG, "showRewardedVideo exception: " + e.getMessage(), e);
            if (listener != null) listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常", response.getSdkName(), 0, e.getMessage()));
        }
    }
}
