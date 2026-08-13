package com.ima.union.core.model.listener;

import com.ima.union.core.model.entry.IFsUnionNativeAd;

/**
 * 信息流自渲染广告对象的回调监听。
 */
public interface FsUnionNativeAdListener {

    /** 广告展示曝光 */
    void onAdShow(IFsUnionNativeAd ad);

    /** 广告被点击 */
    void onAdClick(IFsUnionNativeAd ad);

    /** 广告出错 */
    void onAdError(IFsUnionNativeAd ad, int errorCode, String errorMsg);
}
