package com.ima.union.core.model.entry;

import com.ima.union.core.model.listener.FsUnionInterstitialAdListener;

/**
 * 插屏广告对象对外接口。
 * <p>由 {@link com.ima.union.manager.interstitial.FsInterstitialAdManager#loadAd} 加载成功后返回。</p>
 */
public interface IFsUnionInterstitialAd extends IFsUnionAd {

    /** 展示插屏广告 */
    void show();

    /**
     * 设置广告事件监听器。
     */
    void setListener(FsUnionInterstitialAdListener listener);
}
