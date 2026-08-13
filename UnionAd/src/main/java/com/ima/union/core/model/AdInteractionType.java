package com.ima.union.core.model;

/**
 * 聚合广告 SDK 统一的广告交互类型枚举。
 *
 * <p>各广告平台的交互类型常量各不相同（穿山甲：2=Browser/3=LandingPage/4=Download，
 * 优量汇：无直接交互类型，百青藤：1=LandingPage/2=Download/3=DeepLink，
 * 飞梭：1=DeepLink/2=MiniProgram/3=LandingPage/4=Download），
 * 聚合层在各适配器的 {@link com.ima.union.core.adapter.IFsNativeMaterialProvider#getInteractionType}
 * 实现中完成统一映射，外部业务方无需关心底层 SDK 差异。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * IFsUnionNativeAd ad = ...;
 * if (ad.getInteractionType() == AdInteractionType.DOWNLOAD) {
 *     // 显示下载进度条、合规六要素等
 * }
 * }</pre>
 */
public enum AdInteractionType {

    /** 未知或 SDK 不支持 */
    UNKNOWN(0),

    /** 落地页（H5 / 网页打开） */
    LANDING_PAGE(1),

    /** 应用下载 */
    DOWNLOAD(2),

    /** 深度链接（唤起其他 App） */
    DEEP_LINK(3);

    private final int value;

    AdInteractionType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
