package com.ima.union.core.model.entry;

import android.view.View;
import android.view.ViewGroup;

import com.ima.union.core.model.AdAppMiitInfo;
import com.ima.union.core.model.AdInteractionType;
import com.ima.union.core.model.listener.FsUnionNativeAdListener;

import java.util.List;
import java.util.Map;

/**
 * 信息流自渲染广告对象对外接口。
 * <p>由 {@link com.ima.union.manager.feed_render.FsFeedRenderAdManager#loadAd} 加载成功后返回。</p>
 * <p>媒体可自行使用素材数据完成 UI 渲染，并通过 {@link #reportShow()} / {@link #reportClick()} 上报事件。</p>
 */
public interface IFsUnionNativeAd extends IFsUnionAd {

    // ── 物料素材 ──

    /** 广告标题 */
    String getTitle();

    /** 广告描述 */
    String getDescription();

    /** 图标 URL */
    String getIconUrl();

    /** 主图 URL（单图场景） */
    String getImageUrl();

    /** 多图 URL 列表 */
    List<String> getImageList();

    /** 主图宽度（px），未知返回 0 */
    int getImageWidth();

    /** 主图高度（px），未知返回 0 */
    int getImageHeight();

    /** 行动按钮文字，如"下载"、"查看" */
    String getCallToAction();

    /** 评分（如应用评分，0.0=未知） */
    double getRating();

    /** 视频 URL（视频类信息流），无视频返回 null */
    String getVideoUrl();

    /** 原始平台广告对象（如需访问平台特有属性） */
    Object getNativeAd();

    /** 额外扩展字段 */
    Map<String, Object> getExtra();

    // ── 广告能力信息 ──

    /**
     * 交互类型（聚合统一枚举）。
     *
     * <p>各广告平台的交互类型已被适配器映射为 {@link AdInteractionType} 统一枚举，
     * 外部业务方无需关心底层 SDK 差异。
     *
     * @see AdInteractionType
     */
    AdInteractionType getInteractionType();

    /** 是否为应用下载类广告 */
    boolean isDownloadAd();

    /**
     * 应用包大小（下载类广告），单位字节（Bytes）。
     * 非下载类广告或 SDK 不支持时返回 0。
     */
    long getAppSize();

    // ── 合规信息 ──

    /**
     * 获取工信部合规六要素（应用下载类广告）。
     * 非应用下载类广告返回 null，调用方需判空。
     */
    AdAppMiitInfo getAppMiitInfo();

    /**
     * 注册点击交互区域并设置广告事件监听器。
     *
     * <p>信息流自渲染广告在宿主自行构建 UI 后，调用此方法将可点击 View 注册到平台 SDK，
     * 使 SDK 能够正确处理点击跳转和展示/点击回调。</p>
     *
     * @param containerView  广告容器 ViewGroup
     * @param clickableViews 可点击的 View 列表（通常包括广告主图、标题、CTA 按钮等）
     * @param listener       广告事件监听器（曝光/点击/错误回调）
     */
    void registerViewForInteraction(ViewGroup containerView, List<View> clickableViews, FsUnionNativeAdListener listener);

    // ── 上报接口 ──

    /** 曝光上报（媒体渲染完成后调用） */
    void reportShow();

    /** 点击上报（媒体处理点击事件时调用） */
    void reportClick();

    /** 释放广告资源 */
    void destroy();
}
