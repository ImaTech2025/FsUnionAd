package com.ima.union.core.model.entry;

import com.ima.union.core.model.listener.FsUnionRewardedVideoAdListener;

/**
 * 激励视频广告对象对外接口。
 * <p>由 {@link com.ima.union.manager.rewarded.FsRewardedVideoAdManager#loadAd} 加载成功后返回。</p>
 */
public interface IFsUnionRewardedVideoAd extends IFsUnionAd {

    /** 展示激励视频广告 */
    void show();

    /**
     * 设置广告事件监听器。
     */
    void setListener(FsUnionRewardedVideoAdListener listener);
}
