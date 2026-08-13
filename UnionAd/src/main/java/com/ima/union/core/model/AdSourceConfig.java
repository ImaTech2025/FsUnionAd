package com.ima.union.core.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 广告源配置（同时承担"代码硬编码配置"和"JSON 解析模型"两种角色）。
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code sdkName}      — 广告源名称（String），用于统一识别统计，对应策略 JSON 中的 {@code sdkName} 字段</li>
 *   <li>{@code sdkType}      — 广告平台类型（PANGLE / GDT / BAIDU / FISSION / CUSTOM）</li>
 *   <li>{@code adFormat}     — 广告格式（SPLASH / INTERSTITIAL / REWARDED_VIDEO / FEED_TEMPLATE / FEED_RENDER）</li>
 *   <li>{@code adUnitId}     — 广告平台侧的投放位 ID（由各平台分配）</li>
 *   <li>{@code appId}        — 广告平台侧的应用 ID</li>
 *   <li>{@code token}        — 部分平台需要的鉴权 token（如 Fission）</li>
 *   <li>{@code priority}     — 瀑布流排序优先级，数值越小越优先（从 1 开始）</li>
 *   <li>{@code bidFloor}     — Bidding 底价（单位：分），出价低于此值的竞价结果将被过滤</li>
 *   <li>{@code enabled}      — 是否启用该广告源，false 时策略引擎跳过</li>
 *   <li>{@code timeout}      — 单次广告请求超时时间（ms），默认 5000ms</li>
 *   <li>{@code extraParams}  — 扩展参数，由各适配器自定义解析</li>
 *   <li>{@code bidToken}     — 竞价成功后由适配器回写的 bid token（运行时字段，非配置字段）</li>
 * </ul>
 *
 * <p>说明：策略 ID（{@code strategyId}）仅在 {@link AdUnitConfig} 级别定义，{@code AdSourceConfig}
 * 不再持有该字段。如需追踪一次广告请求所采用的完整策略，请通过所属的 {@code AdUnitConfig.getStrategyId()} 获取。</p>
 *
 * <p>使用方式：</p>
 * <ul>
 *   <li>代码构建：使用 {@link Builder} 创建完整配置实例</li>
 *   <li>JSON 解析：使用 {@link #fromJson(JSONObject)} 反序列化（字段可空）</li>
 * </ul>
 */
public class AdSourceConfig {

    private String sdkName;
    private AdSdkType sdkType;
    private AdFormat adFormat;
    private String adUnitId;
    private String appId;
    private String token;
    private Integer priority;
    private Double bidFloor;
    private Boolean enabled;
    private Long timeout;
    private Map<String, Object> extraParams;
    private String bidToken;
    private Long expireTimeMs;
    private String adapterClassName;

    public AdSourceConfig() {
        this.enabled = Boolean.TRUE;
        this.timeout = 5000L;
        this.priority = 1;
        this.bidFloor = 0.0;
        this.extraParams = new ConcurrentHashMap<>();
    }

    private AdSourceConfig(Builder builder) {
        this.sdkName = builder.sdkName;
        this.sdkType = builder.sdkType;
        this.adFormat = builder.adFormat;
        this.adUnitId = builder.adUnitId;
        this.appId = builder.appId;
        this.token = builder.token;
        this.priority = builder.priority;
        this.bidFloor = builder.bidFloor;
        this.enabled = builder.enabled;
        this.timeout = builder.timeout;
        this.extraParams = builder.extraParams != null ? new ConcurrentHashMap<>(builder.extraParams) : new ConcurrentHashMap<>();
        this.bidToken = builder.bidToken;
        this.expireTimeMs = builder.expireTimeMs;
        this.adapterClassName = builder.adapterClassName;
    }

    // ── Getters / Setters ──

    /**
     * 获取广告源名称。
     * <p>优先级：</p>
     * <ol>
     *   <li>优先返回配置中的 {@code sdkName} 字段值</li>
     *   <li>如果为空，则返回 {@code sdkType} 枚举中的 {@code sdkName} 默认值</li>
     * </ol>
     */
    public String getSdkName() {
        if (sdkName != null && !sdkName.isEmpty()) {
            return sdkName;
        }
        if (sdkType != null) {
            return sdkType.getSdkName();
        }
        return null;
    }

    public void setSdkName(String sdkName) {
        this.sdkName = sdkName;
    }

    public AdSdkType getSdkType() {
        return sdkType;
    }

    public void setSdkType(AdSdkType sdkType) {
        this.sdkType = sdkType;
    }

    public AdFormat getAdFormat() {
        return adFormat;
    }

    public void setAdFormat(AdFormat adFormat) {
        this.adFormat = adFormat;
    }

    public String getAdUnitId() {
        return adUnitId;
    }

    public void setAdUnitId(String adUnitId) {
        this.adUnitId = adUnitId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getPriority() {
        return priority != null ? priority : 1;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public double getBidFloor() {
        return bidFloor != null ? bidFloor : 0.0;
    }

    public void setBidFloor(Double bidFloor) {
        this.bidFloor = bidFloor;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public long getTimeout() {
        return timeout != null ? timeout : 5000L;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public Map<String, Object> getExtraParams() {
        return extraParams != null ? extraParams : Collections.emptyMap();
    }

    public void setExtraParams(Map<String, Object> extraParams) {
        this.extraParams = extraParams;
    }

    public String getBidToken() {
        return bidToken;
    }

    public void setBidToken(String bidToken) {
        this.bidToken = bidToken;
    }

    /**
     * 获取自定义 Adapter 全路径类名。
     * <p>仅自定义 ADN 需要配置（内置平台 PANGLE/GDT/BAIDU/FISSION 不需要）。
     * 聚合 SDK 在首次解析该广告源时，会通过反射 {@code Class.forName()} 创建 Adapter 实例并注册。</p>
     *
     * @return 全路径类名，如 "com.example.ads.MyNetworkSplashAdapter"，未配置返回 null
     */
    public String getAdapterClassName() {
        return adapterClassName;
    }

    public void setAdapterClassName(String adapterClassName) {
        this.adapterClassName = adapterClassName;
    }

    /**
     * 获取广告响应有效期（毫秒）。
     * <p>优先级：</p>
     * <ol>
     *   <li>配置下发正值：直接使用</li>
     *   <li>配置未下发或为 0：返回默认值 30 分钟（1800000 ms）</li>
     * </ol>
     */
    public long getExpireTimeMs() {
        if (expireTimeMs == null || expireTimeMs <= 0) {
            return 1800000L; // 30 分钟默认
        }
        return expireTimeMs;
    }

    /**
     * 设置广告响应有效期（毫秒）。
     * <p>传 {@code null} 或 {@code <= 0} 时，{@link #getExpireTimeMs()} 返回默认值 30 分钟。</p>
     */
    public void setExpireTimeMs(Long expireTimeMs) {
        this.expireTimeMs = expireTimeMs;
    }

    /**
     * 从 JSON 解析广告源配置。所有字段均可空。
     */
    public static AdSourceConfig fromJson(JSONObject json) throws JSONException {
        AdSourceConfig cfg = new AdSourceConfig();
        if (json == null) return cfg;
        if (json.has("sdkName")) cfg.sdkName = json.optString("sdkName", null);
        if (json.has("sdkType")) {
            String type = json.optString("sdkType", null);
            if (type != null && !type.isEmpty()) cfg.sdkType = AdSdkType.fromKey(type);
        }
        if (json.has("adUnitId")) cfg.adUnitId = json.optString("adUnitId", null);
        // 兼容 appId（camelCase）和 appid（小写）两种写法
        if (json.has("appId")) cfg.appId = json.optString("appId", null);
        if (json.has("appid") && cfg.appId == null) cfg.appId = json.optString("appid", null);
        if (json.has("token")) cfg.token = json.optString("token", null);
        if (json.has("priority")) cfg.priority = json.optInt("priority", 1);
        if (json.has("bidFloor")) cfg.bidFloor = json.optDouble("bidFloor", 0);
        if (json.has("enabled")) cfg.enabled = json.optBoolean("enabled", true);
        if (json.has("timeout")) cfg.timeout = json.optLong("timeout", 5000);
        if (json.has("expireTimeMs")) {
            long val = json.optLong("expireTimeMs", 0);
            cfg.expireTimeMs = val > 0 ? val : null; // 0 或负数视为"使用默认"
        }
        if (json.has("adapterClassName")) cfg.adapterClassName = json.optString("adapterClassName", null);
        if (json.has("extraParams")) {
            JSONObject extras = json.optJSONObject("extraParams");
            if (extras != null) {
                Map<String, Object> map = new ConcurrentHashMap<>();
                for (Iterator<String> it = extras.keys(); it.hasNext(); ) {
                    String key = it.next();
                    map.put(key, extras.opt(key));
                }
                cfg.extraParams = map;
            }
        }
        return cfg;
    }

    // ── Builder ──

    public static class Builder {
        private String sdkName;
        private AdSdkType sdkType;
        private AdFormat adFormat;
        private String adUnitId;
        private String appId;
        private String token;
        private int priority = 1;
        private double bidFloor = 0.0;
        private boolean enabled = true;
        private long timeout = 5000;
        private Map<String, Object> extraParams;
        private String bidToken;
        private Long expireTimeMs = null;
        private String adapterClassName;

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

        public Builder appId(String appId) {
            this.appId = appId;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * @param bidFloor Bidding 底价（单位：分），出价低于此值的竞价结果将被过滤
         */
        public Builder bidFloor(double bidFloor) {
            this.bidFloor = bidFloor;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder extraParams(Map<String, Object> extraParams) {
            this.extraParams = extraParams;
            return this;
        }

        public Builder bidToken(String bidToken) {
            this.bidToken = bidToken;
            return this;
        }

        /**
         * 设置广告响应有效期（毫秒）。
         * <p>传 {@code <= 0} 时，{@link #build()} 生成的配置将使用默认值 30 分钟。</p>
         */
        public Builder expireTimeMs(long expireTimeMs) {
            this.expireTimeMs = expireTimeMs > 0 ? expireTimeMs : null;
            return this;
        }

        /**
         * 设置自定义 Adapter 全路径类名。
         * <p>仅自定义 ADN 需要配置，内置平台无需设置。
         * 聚合 SDK 在首次解析该广告源时会通过反射创建 Adapter 实例。</p>
         */
        public Builder adapterClassName(String adapterClassName) {
            this.adapterClassName = adapterClassName;
            return this;
        }

        public AdSourceConfig build() {
            return new AdSourceConfig(this);
        }
    }

    @Override
    public String toString() {
        return "AdSourceConfig{sdkName='" + sdkName
                + "', sdkType=" + sdkType + ", priority=" + priority
                + ", bidFloor=" + bidFloor
                + ", expireTimeMs=" + getExpireTimeMs() + "}";
    }
}
