package com.ima.union.core.model.listener;

import com.ima.union.core.model.entry.IFsUnionRewardedVideoAd;

/**
 * 激励视频广告对象的回调监听。
 */
public interface FsUnionRewardedVideoAdListener {

    /** 广告展示曝光 */
    void onAdShow(IFsUnionRewardedVideoAd ad);

    /** 广告被点击 */
    void onAdClick(IFsUnionRewardedVideoAd ad);

    /** 广告关闭 */
    void onAdClose(IFsUnionRewardedVideoAd ad);

    /** 视频播放完成 */
    void onVideoComplete(IFsUnionRewardedVideoAd ad);

    /** 奖励验证回调 */
    void onRewardVerify(IFsUnionRewardedVideoAd ad, boolean rewardVerify,
                        int rewardAmount, String rewardName);

    /** 广告出错 */
    void onAdError(IFsUnionRewardedVideoAd ad, int errorCode, String errorMsg);
}
