package com.ima.union.core.model;

/**
 * {@link com.ima.union.core.model.UnionAdResponse#getExtra()} / {@link com.ima.union.core.model.entry.IFsUnionNativeAd#getExtra()}
 * 中各物料字段的全局静态常量。所有适配器和业务方应统一引用此处的 key，
 * 避免在多个文件中硬编码相同字符串，保证整体一致性。
 */
public final class AdExtraKeys {

    private AdExtraKeys() {}

    // ── 基础物料 ──
    /** 广告标题 */
    public static final String TITLE = "title";
    /** 广告描述 */
    public static final String DESCRIPTION = "description";

    // ── 图片素材 ──
    /** 图标 URL */
    public static final String ICON_URL = "icon_url";
    /** 主图 URL（单图场景） */
    public static final String IMAGE_URL = "image_url";
    /** 多图 URL 列表（{@code List<String>}） */
    public static final String IMAGE_LIST = "image_list";

    // ── 视频素材 ──
    /** 视频 URL（视频类信息流） */
    public static final String VIDEO_URL = "video_url";

    // ── 互动元素 ──
    /** 行动按钮文字，如"下载"、"查看" */
    public static final String CTA = "cta";
    /** 评分（如应用评分） */
    public static final String RATING = "rating";
}
