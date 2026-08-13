package com.ima.union.core.model.listener;

import com.ima.union.core.model.entry.IFsUnionSplashAd;

/**
 * 开屏广告对象的回调监听。
 */
public interface FsUnionSplashAdListener {

    /** 广告展示曝光 */
    void onAdShow(IFsUnionSplashAd ad);

    /** 广告被点击 */
    void onAdClick(IFsUnionSplashAd ad);

    /** 广告关闭（用户手动关闭或倒计时结束） */
    void onAdClose(IFsUnionSplashAd ad);

    /** 开屏被跳过 */
    void onSplashAdSkipped(IFsUnionSplashAd ad);

    /** 广告出错 */
    void onAdError(IFsUnionSplashAd ad, int errorCode, String errorMsg);
}
