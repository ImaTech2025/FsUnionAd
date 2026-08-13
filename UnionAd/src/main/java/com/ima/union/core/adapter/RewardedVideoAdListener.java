package com.ima.union.core.adapter;

/**
 * 激励视频广告适配器内部回调。
 * <p>继承 {@link AdEventListener}，新增奖励验证回调。展示/点击/关闭/错误直接使用父接口方法。</p>
 */
public interface RewardedVideoAdListener extends AdEventListener {
    /**
     * 奖励验证回调。
     * @param rewardVerify 是否通过验证
     * @param rewardAmount 奖励数量
     * @param rewardName   奖励名称
     */
    void onRewardVerify(boolean rewardVerify, int rewardAmount, String rewardName);

    /** 视频播放完成 */
    default void onVideoComplete() {}
}
