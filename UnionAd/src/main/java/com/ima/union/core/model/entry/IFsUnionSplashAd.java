package com.ima.union.core.model.entry;

import android.view.ViewGroup;

import com.ima.union.core.model.listener.FsUnionSplashAdListener;

/**
 * 开屏广告对象对外接口。
 * <p>由 {@link com.ima.union.manager.splash.FsSplashAdManager#loadAd} 加载成功后返回。</p>
 */
public interface IFsUnionSplashAd extends IFsUnionAd {

    /**
     * 展示开屏广告。
     *
     * @param container 外部传入的展示容器（ViewGroup）
     */
    void show(ViewGroup container);

    /**
     * 设置广告事件监听器。
     */
    void setListener(FsUnionSplashAdListener listener);
}
