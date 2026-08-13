package com.ima.union.core.model;

import com.ima.union.core.adapter.AdAdapter;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聚合广告统一响应 — 合并自原 {@code AdObject} + {@code BidResponse}。
 *
 * <p>设计：不可变对象，通过 {@link Builder} 构建。</p>
 *
 * <p>竞败缓存抢令牌标志：防止并发同 slotId 的 bidding 重复从竞败缓存中取出同一份广告。</p>
 *
 * <h3>广告就绪检查 {@link #isReady()}</h3>
 * <p>广告加载成功后，素材可能随版本/缓存/过期而变化。部分 SDK（如百度各广告类）提供了就绪检查方法。
 * 适配器在构建响应时读取对应 SDK 广告对象的 {@code isReady()} 值。
 * 对于没有提供该检查的 SDK，默认值 {@code true}。</p>
 * <p><b>调用方应在展示广告前调用 {@code isReady()} 做二次确认</b>，避免展示已失效的广告。</p>
 */
public class UnionAdResponse {

    private final String sdkName;
    private final AdSdkType sdkType;
    private final AdFormat adFormat;
    private final String adUnitId;
    private final double ecpm;
    private final long expireTimeMs;
    private final boolean fromCache;
    private final Object nativeAd;
    private final Map<String, Object> extra;
    private final boolean ready;
    private final AdAdapter adapter;
    private final AtomicBoolean claimed = new AtomicBoolean(false);

    private UnionAdResponse(Builder builder) {
        this.sdkName = builder.sdkName;
        this.sdkType = builder.sdkType;
        this.adFormat = builder.adFormat;
        this.adUnitId = builder.adUnitId;
        this.ecpm = builder.ecpm;
        this.expireTimeMs = builder.expireTimeMs;
        this.fromCache = builder.fromCache;
        this.nativeAd = builder.nativeAd;
        this.extra = builder.extra != null ? builder.extra : Collections.emptyMap();
        this.ready = builder.ready;
        this.adapter = builder.adapter;
    }

    public String getSdkName() {
        return sdkName;
    }

    public AdSdkType getSdkType() {
        return sdkType;
    }

    public AdFormat getAdFormat() {
        return adFormat;
    }

    public String getAdUnitId() {
        return adUnitId;
    }

    /**
     * eCPM（单位：分）。
     * <p>竞价场景下：平台返回的报价；用于竞价链路底价过滤与排序。</p>
     * <p>瀑布流场景下：策略配置中预设的 eCPM；用于瀑布流链路排序。</p>
     */
    public double getEcpm() {
        return ecpm;
    }

    /**
     * 竞价响应过期时间戳（ms）。非竞价场景下为 0。
     */
    public long getExpireTimeMs() {
        return expireTimeMs;
    }

    /**
     * 是否来自竞败缓存（即本次 bidding 复用上次竞败的广告）。
     * <p>实时召回的广告为 {@code false}；从竞败缓存取出参与本次比价的广告为 {@code true}。</p>
     */
    public boolean isFromCache() {
        return fromCache;
    }

    public Object getNativeAd() {
        return nativeAd;
    }

    /**
     * 获取创建此响应的适配器实例，用于竞败上报等操作。
     *
     * @return 创建此响应的 {@link AdAdapter}，若未设置则返回 {@code null}
     */
    public AdAdapter getAdapter() {
        return adapter;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    /**
     * 检查当前广告是否处于就绪状态（可正常展示）。
     *
     * <p>取值来源：各适配器在构建响应时读取对应 SDK 广告对象的 {@code isReady()} 方法。
     * 对于没有提供就绪检查的 SDK，默认 {@code true}。</p>
     *
     * <p><b>调用方应在展示广告前调用此方法</b>，避免展示已失效的广告素材。
     * 例如百度 {@code SplashAd.isReady()} 在广告过期后会返回 {@code false}。</p>
     *
     * @return true 表示广告可正常展示，false 表示已失效
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * 判断该竞价响应是否已过期。
     * <p>仅当 {@code expireTimeMs > 0} 且当前时间超过过期时间时返回 {@code true}。</p>
     *
     * @param now 当前时间戳（ms），通常由 {@code System.currentTimeMillis()} 传入
     * @return 已过期返回 {@code true}，否则 {@code false}
     */
    public boolean isExpired(long now) {
        return expireTimeMs > 0 && expireTimeMs <= now;
    }

    /**
     * 竞败缓存抢令牌：CAS 把 {@code claimed} 从 {@code false} 置为 {@code true}。
     * <p>抢到者（返回 {@code true}）才有权把这份广告从缓存取出；抢不到（返回 {@code false}）
     * 说明已被并发其他 bidding 抢走，应跳过此条继续尝试缓存中其他广告。</p>
     *
     * @return true 表示抢到，false 表示已被抢
     */
    public boolean tryClaim() {
        return claimed.compareAndSet(false, true);
    }

    public static class Builder {
        private String sdkName;
        private AdSdkType sdkType;
        private AdFormat adFormat;
        private String adUnitId;
        private double ecpm;
        private long expireTimeMs;
        private boolean fromCache = false;
        private Object nativeAd;
        private Map<String, Object> extra;
        private boolean ready = true;
        private AdAdapter adapter;

        public Builder sdkName(String sdkName) {
            this.sdkName = sdkName;
            return this;
        }

        public Builder sdkType(AdSdkType sdkType) {
            this.sdkType = sdkType;
            return this;
        }

        public Builder adFormat(AdFormat adFormat) {
            this.adFormat = adFormat;
            return this;
        }

        public Builder adUnitId(String adUnitId) {
            this.adUnitId = adUnitId;
            return this;
        }

        public Builder ecpm(double ecpm) {
            this.ecpm = ecpm;
            return this;
        }

        public Builder expireTimeMs(long expireTimeMs) {
            this.expireTimeMs = expireTimeMs;
            return this;
        }

        public Builder fromCache(boolean fromCache) {
            this.fromCache = fromCache;
            return this;
        }

        public Builder nativeAd(Object nativeAd) {
            this.nativeAd = nativeAd;
            return this;
        }

        public Builder extra(Map<String, Object> extra) {
            this.extra = extra;
            return this;
        }

        /**
         * 设置广告就绪状态，默认为 {@code true}。
         * <p>各适配器在构建响应时应读取对应 SDK 广告对象的就绪状态（如有）
         * 并通过此方法传入。未传则默认 {@code true}。</p>
         */
        public Builder ready(boolean ready) {
            this.ready = ready;
            return this;
        }

        /**
         * 设置创建此响应的适配器实例。
         * <p>各适配器在 {@code buildResponse()} 中传入 {@code this}，
         * 供策略层通过 {@code response.getAdapter().reportBidFail()} 进行竞败上报。</p>
         */
        public Builder adapter(AdAdapter adapter) {
            this.adapter = adapter;
            return this;
        }

        public UnionAdResponse build() {
            return new UnionAdResponse(this);
        }
    }
}
