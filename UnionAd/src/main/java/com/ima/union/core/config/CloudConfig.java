package com.ima.union.core.config;

import com.ima.union.core.model.AdUnitConfig;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 云端或本地默认的聚合配置模型。
 * <p>结构为顶层全局配置（仅 {@code version}）+ 单条 {@link AdUnitConfig}（即当前请求的 slotId 配置）。</p>
 *
 * 
 */
public class CloudConfig {

    private long version;
    private AdUnitConfig strategy;

    public CloudConfig() {
        this.strategy = null;
    }

    // ── Getters / Setters ──

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public AdUnitConfig getStrategy() {
        return strategy;
    }

    public void setStrategy(AdUnitConfig strategy) {
        this.strategy = strategy;
    }

    /**
     * 兼容旧接口：返回当前 {@code strategy}（即当前请求 slotId 的 AdUnitConfig）。
     * <p>保留方法名以兼容旧调用方（{@link com.ima.union.core.config.CloudStrategyManager#parseDefaultStrategyJson}），
     * 实际上 Map 已被扁平化为单条配置，{@code slotId} 参数不再被使用。</p>
     */
    public AdUnitConfig getAdUnitConfig(String slotId) {
        return strategy;
    }

    /**
     * 从 JSON 字符串解析
     */
    public static CloudConfig fromJson(String jsonString) throws JSONException {
        return fromJson(new JSONObject(jsonString));
    }

    /**
     * 从 JSON 对象解析。
     * <p>仅识别顶层 {@code version} 字段；{@code strategy} 字段为必填（单条 {@link AdUnitConfig}）。
     * 其内部 {@code slotId} 字段由调用方在 {@code AdRequestParams} 中传入并做匹配校验。</p>
     */
    public static CloudConfig fromJson(JSONObject json) throws JSONException {
        CloudConfig config = new CloudConfig();
        if (json == null) return config;
        if (json.has("version")) config.version = json.optLong("version", 0);

        if (json.has("strategy")) {
            JSONObject unitJson = json.optJSONObject("strategy");
            if (unitJson != null) {
                config.strategy = AdUnitConfig.fromJson(unitJson);
            }
        }

        return config;
    }
}
