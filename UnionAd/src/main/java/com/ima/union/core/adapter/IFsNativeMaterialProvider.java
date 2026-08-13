package com.ima.union.core.adapter;

import com.ima.union.core.model.AdAppMiitInfo;
import com.ima.union.core.model.AdInteractionType;

import java.util.Collections;
import java.util.List;

/**
 * 信息流自渲染广告素材提取接口。
 *
 * <p>各平台的信息流自渲染适配器实现此接口，直接从平台 SDK 的原生广告对象
 * 提取素材数据（标题、描述、图片 URL、图片尺寸等），替代通过 Map 中转的方式。</p>
 *
 * <p>{@link com.ima.union.core.model.impl.FsUnionNativeAd} 在初始化时检查适配器
 * 是否实现了此接口，若实现则通过此接口获取素材。</p>
 *
 * <p>实现示例（穿山甲）：</p>
 * <pre>{@code
 * public class PangleFeedRenderAdapter extends PangleBaseAdapter
 *         implements FeedAdAdapter, IFsNativeMaterialProvider {
 *     &#64;Override
 *     public String getTitle(Object nativeAd) {
 *         return nativeAd instanceof TTFeedAd
 *                 ? ((TTFeedAd) nativeAd).getTitle() : null;
 *     }
 *     // ... 其他方法类似
 * }
 * }</pre>
 */
public interface IFsNativeMaterialProvider {

    // ════════════════════════════════════════════════════════════════
    //  基础素材
    // ════════════════════════════════════════════════════════════════

    /** 广告标题 */
    default String getTitle(Object nativeAd) { return null; }

    /** 广告描述 */
    default String getDescription(Object nativeAd) { return null; }

    /** 图标 URL */
    default String getIconUrl(Object nativeAd) { return null; }

    /** 主图 URL（单图场景） */
    default String getImageUrl(Object nativeAd) { return null; }

    /** 多图 URL 列表 */
    default List<String> getImageList(Object nativeAd) { return Collections.emptyList(); }

    /** 主图宽度（px），未知返回 0 */
    default int getImageWidth(Object nativeAd) { return 0; }

    /** 主图高度（px），未知返回 0 */
    default int getImageHeight(Object nativeAd) { return 0; }

    /** 行动按钮文字（如"下载"、"查看"） */
    default String getCallToAction(Object nativeAd) { return null; }

    /** 评分（如应用评分，0.0=未知） */
    default double getRating(Object nativeAd) { return 0.0; }

    /** 视频 URL（视频类信息流），无视频返回 null */
    default String getVideoUrl(Object nativeAd) { return null; }

    // ════════════════════════════════════════════════════════════════
    //  广告能力信息
    // ════════════════════════════════════════════════════════════════

    /**
     * 交互类型（聚合统一枚举）。
     *
     * <p>各平台 SDK 的交互类型常量互不相同，适配器在此方法中完成映射，
     * 将各平台原始值转为 {@link AdInteractionType} 统一枚举：
     *
     * <table>
     *   <tr><th>聚合</th><th>穿山甲</th><th>优量汇</th><th>百青藤</th><th>飞梭</th></tr>
     *   <tr><td>LANDING_PAGE</td><td>BROWSER(2) / LANDING_PAGE(3)</td><td>非App下载</td><td>LANDING_PAGE(1)</td><td>MINI_PROGRAM(2) / LANDING_PAGE(3)</td></tr>
     *   <tr><td>DOWNLOAD</td><td>DOWNLOAD(4)</td><td>isAppAd()</td><td>APP_DOWNLOAD(2)</td><td>DOWNLOAD(4)</td></tr>
     *   <tr><td>DEEP_LINK</td><td>—</td><td>—</td><td>DEEP_LINK(3)</td><td>DEEPLINK(1)</td></tr>
     * </table>
     *
     * @see AdInteractionType
     */
    default AdInteractionType getInteractionType(Object nativeAd) { return AdInteractionType.UNKNOWN; }

    /** 是否为应用下载类广告（基于 {@link #getInteractionType} 判定） */
    default boolean isDownloadAd(Object nativeAd) {
        return getInteractionType(nativeAd) == AdInteractionType.DOWNLOAD;
    }

    /**
     * 应用包大小（下载类广告），单位字节（Bytes）。
     * 非下载类广告返回 0。
     */
    default long getAppSize(Object nativeAd) { return 0L; }

    // ════════════════════════════════════════════════════════════════
    //  合规信息
    // ════════════════════════════════════════════════════════════════

    /**
     * 工信部合规六要素（应用下载类广告）。
     * 非应用下载类广告返回 null，调用方需判空。
     */
    default AdAppMiitInfo getAppMiitInfo(Object nativeAd) { return null; }
}
