package com.ima.union.adapters.baidu;

import com.ima.union.utils.FsLogger;

import android.content.Context;

import com.baidu.mobads.sdk.api.RewardVideoAd;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.FsUnionListenerBridge;
import com.ima.union.core.adapter.RewardedVideoAdAdapter;
import com.ima.union.core.adapter.RewardedVideoAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.BidLossReason;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 百青藤激励视频广告适配器。
 */
public class BaiduRewardedVideoAdapter extends BaiduBaseAdapter implements RewardedVideoAdAdapter {

    private static final String TAG = "BaiduRewardedVideoAdapter";


    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        try {
            String adPlaceId = sourceConfig.getAdUnitId();
            // 桥接器：请求阶段注入 SDK 回调，展示阶段绑定 RewardedVideoAdListener
            FsUnionListenerBridge<RewardedVideoAdListener> showBridge = new FsUnionListenerBridge<>();

            BaiduRewardedCallback handler = new BaiduRewardedCallback(sourceConfig, callback, showBridge);
            RewardVideoAd ad = new RewardVideoAd(context, adPlaceId, handler);
            handler.bindAd(ad);

            if (sourceConfig.getBidFloor() > 0) {
                ad.setBidFloor((int) sourceConfig.getBidFloor());
            }
            ad.load();
        } catch (Exception e) {
            FsLogger.e(TAG, "requestRewardedVideo failed: " + e.getMessage());
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sourceConfig.getSdkName(), 0, e.getMessage()));
        }
    }

    @Override
    public void showRewardedVideo(Context context, UnionAdResponse response, RewardedVideoAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "showRewardedVideo: response is null");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        FsLogger.d(TAG, "▶ showRewardedVideo: sourceId=" + response.getSdkName());
        RewardVideoAd ad = (RewardVideoAd) response.getNativeAd();
        if (ad == null) {
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return;
        }
        try {
            // 从 extra 取出桥接器并绑定当前 listener
            java.util.Map<String, Object> extra = response.getExtra();
            if (extra != null) {
                FsUnionListenerBridge<RewardedVideoAdListener> showBridge = (FsUnionListenerBridge<RewardedVideoAdListener>) extra.get(FsUnionListenerBridge.EXTRA_KEY);
                if (showBridge != null) showBridge.bind(listener);
            }
            ad.show();
            FsLogger.d(TAG, "showRewardedVideo");
        } catch (Exception e) {
            FsLogger.e(TAG, "showRewardedVideo exception: " + e.getMessage());
            if (listener != null)
                listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常"));
        }
    }

    // ── 加载+运行时回调 ──

    private class BaiduRewardedCallback implements RewardVideoAd.RewardVideoAdListener {
        private final AdSourceConfig sourceConfig;
        private final AdCallback callback;
        private final FsUnionListenerBridge<RewardedVideoAdListener> showBridge;
        private final AtomicBoolean closedGuard = new AtomicBoolean(false);
        private RewardVideoAd ad;

        BaiduRewardedCallback(AdSourceConfig sourceConfig, AdCallback callback, FsUnionListenerBridge<RewardedVideoAdListener> showBridge) {
            this.sourceConfig = sourceConfig;
            this.callback = callback;
            this.showBridge = showBridge;
        }

        void bindAd(RewardVideoAd ad) {
            this.ad = ad;
        }

        @Override
        public void onAdLoaded() {
            if (ad != null) {
                double ecpm = getEcpmFromAd(ad);
                Map<String, Object> extra = new HashMap<>();
                extra.put("is_bidding", true);
                extra.put(FsUnionListenerBridge.EXTRA_KEY, showBridge);
                if (callback != null)
                    callback.onLoaded(buildResponse(sourceConfig, AdFormat.REWARDED_VIDEO, ad, ecpm, extra, readAdIsReady(ad)));
            }
        }

        @Override
        public void onVideoDownloadSuccess() {
            if (ad != null) {
                double ecpm = getEcpmFromAd(ad);
                Map<String, Object> extra = new HashMap<>();
                extra.put("is_bidding", true);
                extra.put(FsUnionListenerBridge.EXTRA_KEY, showBridge);
                if (callback != null)
                    callback.onCachedSuccess(buildResponse(sourceConfig, AdFormat.REWARDED_VIDEO, ad, ecpm, extra, readAdIsReady(ad)));
            }
        }

        @Override
        public void onAdFailed(String reason) {
            reportBidFail(ad, BidLossReason.LOAD_FAILED);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sourceConfig.getSdkName(), -1, reason));
        }

        @Override
        public void onVideoDownloadFailed() {
            FsLogger.e(TAG, "BaiduRewardedVideo.onVideoDownloadFailed: video download failed after ad loaded");
            // 视频下载失败发生在 onAdLoaded 之后，不应再报 loadFailed
            // 如果已在展示阶段，通知 onAdError；否则仅日志记录
            RewardedVideoAdListener l = showBridge.get();
            if (l != null) {
                l.onAdError(FsAdErrorCode.PLAYBACK_ERROR, FsAdErrorCode.buildMsg("视频播放错误"));
            }
        }

        // 运行时回调：通过桥接器转发
        @Override
        public void onAdShow() {
            RewardedVideoAdListener l = showBridge.get();
            if (l != null) l.onAdShow();
        }

        @Override
        public void onAdClick() {
            RewardedVideoAdListener l = showBridge.get();
            if (l != null) l.onAdClick();
        }

        @Override
        public void onAdClose(float playScale) {
            if (closedGuard.compareAndSet(false, true)) {
                RewardedVideoAdListener l = showBridge.get();
                if (l != null) {
                    l.onAdClose();
                    showBridge.clear();
                }
            }
        }

        @Override
        public void onRewardVerify(boolean rewardVerify) {
            RewardedVideoAdListener l = showBridge.get();
            if (l != null)
                l.onRewardVerify(rewardVerify, 0, "");
        }

        @Override
        public void playCompletion() {
            RewardedVideoAdListener l = showBridge.get();
            if (l != null) l.onVideoComplete();
        }

        @Override
        public void onAdSkip(float playScale) {
            if (closedGuard.compareAndSet(false, true)) {
                RewardedVideoAdListener l = showBridge.get();
                if (l != null) {
                    l.onAdClose();
                    showBridge.clear();
                }
            }
        }
    }
}
