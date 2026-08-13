package com.ima.union.core.model.entry;

import android.view.View;

import com.ima.union.core.model.listener.FsUnionNativeExpressAdListener;

/**
 * 信息流模板广告对象对外接口。
 * <p>由 {@link com.ima.union.manager.feed_template.FsFeedTemplateAdManager#loadAd} 加载成功后返回。</p>
 */
public interface IFsUnionNativeExpressAd extends IFsUnionAd {

    /**
     * 获取模板渲染后的广告 View。
     * <p>首次调用会触发适配器的 render 逻辑，后续调用返回缓存的 View。</p>
     *
     * @return 模板广告 View，如果适配器不支持或渲染失败则返回 null
     */
    View getExpressView();

    /** 返回模板 View 是否已渲染 */
    boolean isViewRendered();

    /** 设置广告事件监听器 */
    void setListener(FsUnionNativeExpressAdListener listener);

    /** 释放广告资源 */
    void destroy();
}
