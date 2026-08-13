package com.ima.union.core.model.listener;

import com.ima.union.core.model.entry.IFsUnionNativeExpressAd;

/**
 * 信息流模板广告对象的回调监听。
 */
public interface FsUnionNativeExpressAdListener {

    /** 广告展示曝光 */
    void onAdShow(IFsUnionNativeExpressAd ad);

    /** 广告被点击 */
    void onAdClick(IFsUnionNativeExpressAd ad);

    /** 模板 View 渲染完成 */
    void onExpressAdRendered(IFsUnionNativeExpressAd ad);

    /** 广告关闭/销毁 */
    void onAdClose(IFsUnionNativeExpressAd ad);

    /** 广告出错 */
    void onAdError(IFsUnionNativeExpressAd ad, int errorCode, String errorMsg);
}
