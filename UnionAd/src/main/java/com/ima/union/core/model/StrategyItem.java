package com.ima.union.core.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 广告位内"策略阶段"配置（同时承担"代码硬编码配置"和"JSON 解析模型"两种角色）。
 *
 * <p>每个 {@link StrategyItem} 代表一次广告请求中可独立执行的一个策略阶段，多个 {@code StrategyItem}
 * 通过 {@link AdUnitConfig#getStrategies()} 组成一条按 {@link #getPriority() priority} 升序串联的
 * 降级链路（priority chain）。前一个阶段返回 {@code noFill}（无广告可返回）时，才推进到下一个阶段；
 * 同 priority 阶段内部由对应 strategy（BIDDING 并发 / WATERFALL 串行）自行调度。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code type}      — 策略类型（WATERFALL / BIDDING）。<b>混合语义</b>由 {@code strategies} 数组长度
 *       >= 2 隐式表达，{@link StrategyType#HYBRID} 已被移除，不再有独立枚举项。</li>
 *   <li>{@code priority}  — 阶段执行优先级，数值越小越先执行（从 1 开始）。当同一广告位内多阶段 priority
 *       相同时，按 {@code strategies} 数组声明顺序执行；同阶段内部 source 排序由
 *       {@link AdSourceConfig#getPriority()} 控制（仅 WaterfallStrategy 读取）。</li>
 *   <li>{@code timeoutMs} — 该阶段的最大执行超时时间（ms），仅控制当前阶段，不向下游阶段透传。
 *       默认 2000ms；阶段内 strategy 自管超时切换。</li>
 *   <li>{@code sources}   — 该阶段参与竞投 / 排序的广告源列表。阶段之间 sources 完全独立，配置可重叠。</li>
 * </ul>
 *
 * <p>使用方式：</p>
 * <ul>
 *   <li>代码构建：使用 {@link Builder} 创建完整配置实例</li>
 *   <li>JSON 解析：使用 {@link #fromJson(JSONObject)} 反序列化（字段可空）</li>
 * </ul>
 */
public class StrategyItem {

    private StrategyType type;
    private Integer priority;
    private Long timeoutMs;
    private List<AdSourceConfig> sources;

    public StrategyItem() {
        this.type = StrategyType.WATERFALL;
        this.priority = 1;
        this.timeoutMs = 2000L;
        this.sources = new CopyOnWriteArrayList<>();
    }

    private StrategyItem(Builder builder) {
        this.type = builder.type != null ? builder.type : StrategyType.WATERFALL;
        this.priority = builder.priority > 0 ? builder.priority : 1;
        this.timeoutMs = builder.timeoutMs > 0 ? builder.timeoutMs : 2000L;
        this.sources = builder.sources != null ? new CopyOnWriteArrayList<>(builder.sources) : new CopyOnWriteArrayList<>();
    }

    // ── Getters / Setters ──

    public StrategyType getType() {
        return type != null ? type : StrategyType.WATERFALL;
    }

    public void setType(StrategyType type) {
        this.type = type;
    }

    public int getPriority() {
        return priority != null ? priority : 1;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public long getTimeoutMs() {
        return timeoutMs != null && timeoutMs > 0 ? timeoutMs : 2000L;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public List<AdSourceConfig> getSources() {
        return sources != null ? sources : Collections.emptyList();
    }

    public void setSources(List<AdSourceConfig> sources) {
        this.sources = sources;
    }

    /**
     * 从 JSON 解析策略阶段配置。所有字段均可空，未配置时使用 {@link #StrategyItem()} 的默认值。
     *
     * <p>{@code type} 字段必须是 {@link StrategyType#WATERFALL} 或 {@link StrategyType#BIDDING}，
     * 未知值或不存在的 {@code type} 字段一律回退为 {@link StrategyType#WATERFALL}。
     * 混合策略不再以 {@code "HYBRID"} 字符串表达，由所属 {@code AdUnitConfig} 的 {@code strategies} 数组
     * 长度 >= 2 隐式承载。</p>
     */
    public static StrategyItem fromJson(JSONObject json) throws JSONException {
        StrategyItem item = new StrategyItem();
        if (json == null) return item;
        if (json.has("type")) {
            String t = json.optString("type", null);
            if (t != null && !t.isEmpty()) {
                try {
                    item.type = StrategyType.valueOf(t);
                } catch (IllegalArgumentException ignored) {
                    // 未知枚举值时使用默认 WATERFALL
                }
            }
        }
        if (json.has("priority")) item.priority = json.optInt("priority", 1);
        if (json.has("timeoutMs")) item.timeoutMs = json.optLong("timeoutMs", 2000);
        if (json.has("sources")) {
            JSONArray arr = json.optJSONArray("sources");
            if (arr != null) {
                List<AdSourceConfig> list = new CopyOnWriteArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(AdSourceConfig.fromJson(arr.getJSONObject(i)));
                }
                item.sources = list;
            }
        }
        return item;
    }

    // ── Builder ──

    public static class Builder {
        private StrategyType type;
        private int priority = 1;
        private long timeoutMs = 2000;
        private List<AdSourceConfig> sources;

        public Builder type(StrategyType type) {
            this.type = type;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder sources(List<AdSourceConfig> sources) {
            this.sources = sources;
            return this;
        }

        public StrategyItem build() {
            return new StrategyItem(this);
        }
    }

    @Override
    public String toString() {
        return "StrategyItem{type=" + type + ", priority=" + getPriority()
                + ", timeoutMs=" + getTimeoutMs()
                + ", sources=" + (sources == null ? 0 : sources.size()) + "}";
    }
}
