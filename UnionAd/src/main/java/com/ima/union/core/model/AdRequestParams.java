package com.ima.union.core.model;

/**
 * 广告请求参数，使用 Builder 模式构建。
 * <p>包含广告位 ID（slotId）、默认策略 JSON 等请求级配置。</p>
 *
 * <p><b>尺寸字段语义</b>：</p>
 * <ul>
 *   <li>{@link #getExpressViewAcceptedSize()} — 模板广告（{@code FEED_TEMPLATE}）使用，
 *       单位 <b>dp</b>，对应 Pangle SDK 的 {@code setExpressViewAcceptedSize} 接口</li>
 *   <li>{@link #getImageAcceptedSize()} — 信息流自渲染广告（{@code FEED_RENDER}）使用，
 *       单位 <b>px</b>，对应 Pangle SDK 的 {@code setImageAcceptedSize} 接口</li>
 * </ul>
 *
 * <p><b>边界处理</b>：任意字段未设置或 {@code <= 0} 时，适配器在调用三方 SDK 时
 * <b>不</b>透传该尺寸字段，由三方 SDK 使用自身默认值；
 * 这样可以避免因尺寸传错导致模板/图片渲染异常。</p>
 */
public class AdRequestParams {

    private final String slotId;
    private final String defaultStrategyJson;

    /** 模板广告尺寸(宽,高) — 单位 dp, 0/null 表示不设置(由三方 SDK 自行处理) */
    private final int expressViewWidthDp;
    private final int expressViewHeightDp;

    /** 信息流自渲染图片尺寸(宽,高) — 单位 px, 0/null 表示不设置 */
    private final int imageWidthPx;
    private final int imageHeightPx;

    /** 视频广告是否静音播放，默认 false（有声）。仅对支持静音控制的 SDK 生效（Fission/GDT 插屏等）。 */
    private final boolean videoMuted;

    private AdRequestParams(Builder builder) {
        this.slotId = builder.slotId;
        this.defaultStrategyJson = builder.defaultStrategyJson;
        this.expressViewWidthDp = builder.expressViewWidthDp;
        this.expressViewHeightDp = builder.expressViewHeightDp;
        this.imageWidthPx = builder.imageWidthPx;
        this.imageHeightPx = builder.imageHeightPx;
        this.videoMuted = builder.videoMuted;
    }

    public String getSlotId() {
        return slotId;
    }

    public String getDefaultStrategyJson() {
        return defaultStrategyJson;
    }

    /**
     * 获取模板广告接受的尺寸(宽,高)，单位 dp。
     * <p>返回 {@code null} 表示外部未设置，适配器应<b>不</b>透传给三方 SDK。</p>
     */
    public int[] getExpressViewAcceptedSize() {
        if (expressViewWidthDp <= 0 || expressViewHeightDp <= 0) return null;
        return new int[]{expressViewWidthDp, expressViewHeightDp};
    }

    /**
     * 获取信息流自渲染接受的图片尺寸(宽,高)，单位 px。
     * <p>返回 {@code null} 表示外部未设置，适配器应<b>不</b>透传给三方 SDK。</p>
     */
    public int[] getImageAcceptedSize() {
        if (imageWidthPx <= 0 || imageHeightPx <= 0) return null;
        return new int[]{imageWidthPx, imageHeightPx};
    }

    /**
     * 视频广告是否静音播放，默认 {@code false}（有声）。
     * <p>仅对支持静音控制的 SDK 生效（当前：Fission 全格式、GDT 插屏）。
     * Pangle/Baidu 当前 SDK 版本不提供静音 API，此配置对其无效。</p>
     */
    public boolean getVideoMuted() {
        return videoMuted;
    }

    public static class Builder {
        private String slotId;
        private String defaultStrategyJson;
        private int expressViewWidthDp = 0;
        private int expressViewHeightDp = 0;
        private int imageWidthPx = 0;
        private int imageHeightPx = 0;
        private boolean videoMuted = false;

        /**
         * 设置广告位 ID（必填）。
         * <p>格式：8 位数字字符串，如 "10000001"。</p>
         */
        public Builder slotId(String slotId) {
            this.slotId = slotId;
            return this;
        }

        /**
         * 设置默认策略 JSON（可选）。
         * <p>字段结构与 {@link com.ima.union.core.config.CloudConfig} 一致。</p>
         */
        public Builder defaultStrategyJson(String defaultStrategyJson) {
            this.defaultStrategyJson = defaultStrategyJson;
            return this;
        }

        /**
         * 设置<b>模板广告</b>接受的尺寸（宽,高），单位 <b>dp</b>。
         * <p>对应 Pangle SDK 的 {@code setExpressViewAcceptedSize} 接口。
         * 适用于 {@code AdFormat.FEED_TEMPLATE}。</p>
         *
         * <p>外部不调用此方法，或传入任一维度 {@code <= 0}，则适配器不会向三方 SDK
         * 透传该尺寸（避免因尺寸错误导致展示异常）。</p>
         */
        public Builder setExpressViewAcceptedSize(int widthDp, int heightDp) {
            this.expressViewWidthDp = widthDp;
            this.expressViewHeightDp = heightDp;
            return this;
        }

        /**
         * 设置<b>信息流自渲染</b>广告的图片尺寸（宽,高），单位 <b>px</b>。
         * <p>对应 Pangle SDK 的 {@code setImageAcceptedSize} 接口。
         * 适用于 {@code AdFormat.FEED_RENDER}。</p>
         *
         * <p>参考 Pangle 75xx 及以上版本：仅需调用 {@code setExpressViewAcceptedSize(dp)}
         * 即可；为兼容低版本（需同时设置 {@code setImageAcceptedSize}），
         * 业务方通常同时设置两个尺寸接口。本 SDK 在适配器层根据 adFormat 判断是否透传：
         * 模板广告仅用 {@code setExpressViewAcceptedSize}，自渲染同时透传两个接口。</p>
         *
         * <p>外部不调用此方法，或传入任一维度 {@code <= 0}，则适配器不会向三方 SDK
         * 透传该尺寸（避免因尺寸错误导致展示异常）。</p>
         */
        public Builder setImageAcceptedSize(int widthPx, int heightPx) {
            this.imageWidthPx = widthPx;
            this.imageHeightPx = heightPx;
            return this;
        }

        /**
         * 设置视频广告是否静音播放（默认 {@code false}，有声）。
         * <p>仅对支持静音控制的 SDK 生效：Fission 全格式（通过 FissionSlot.VIDEO_MUTE）、
         * GDT 插屏（通过 VideoOption）。Pangle/Baidu 当前版本不支持，设置无效。</p>
         *
         * @param videoMuted true=静音，false=有声
         */
        public Builder videoMuted(boolean videoMuted) {
            this.videoMuted = videoMuted;
            return this;
        }

        public AdRequestParams build() {
            if (slotId == null || slotId.isEmpty()) {
                throw new IllegalArgumentException("slotId is required");
            }
            return new AdRequestParams(this);
        }
    }
}
