package com.ima.union.core.model.listener;

import com.ima.union.core.model.entry.IFsUnionInterstitialAd;

/**
 * 插屏广告对象的回调监听。
 */
public interface FsUnionInterstitialAdListener {

    /** 广告展示曝光 */
    void onAdShow(IFsUnionInterstitialAd ad);

    /** 广告被点击 */
    void onAdClick(IFsUnionInterstitialAd ad);

    /** 广告关闭 */
    void onAdClose(IFsUnionInterstitialAd ad);

    /** 广告出错 */
    void onAdError(IFsUnionInterstitialAd ad, int errorCode, String errorMsg);
}
