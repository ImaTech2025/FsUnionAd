package com.ima.union.core.model;

import com.ima.union.core.config.CloudConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 广告位配置（同时承担"代码硬编码配置"和"JSON 解析模型"两种角色）。
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code slotId}           — 广告位 ID（8 位数字字符串，与 {@link AdRequestParams#getSlotId()}
 *       运行时强制一致校验）。</li>
 *   <li>{@code strategyId}       — 策略 ID，用于追踪一次广告请求所采用的策略来源（本地默认 / 云端下发），默认 {@code "default"}</li>
 *   <li>{@code adFormat}         — 广告格式（SPLASH / INTERSTITIAL / REWARDED_VIDEO / FEED_TEMPLATE / FEED_RENDER）</li>
 *   <li>{@code refreshInterval}  — 自动刷新间隔（ms），0 表示不自动刷新</li>
 *   <li>{@code strategies}       — 该广告位下配置的所有策略阶段，按 {@link StrategyItem#getPriority() priority}
 *       升序串联成一条降级链路（priority chain）。当 {@code strategies.size() == 1} 时为单策略（仅 BIDDING 或
 *       WATERFALL），当 {@code strategies.size() >= 2} 时为链式（{@link StrategyType#HYBRID}），
 *       前一阶段返回 {@code noFill} 才推进到下一阶段。</li>
 * </ul>
 *
 * 
 *
 * <p>使用方式：</p>
 * <ul>
 *   <li>代码构建：使用 {@link Builder} 创建完整配置实例</li>
 *   <li>JSON 解析：使用 {@link #fromJson(JSONObject)} 或 {@link CloudConfig#fromJson(String)} 反序列化
 *       （字段可空，未配置时使用默认值）</li>
 * </ul>
 */
public class AdUnitConfig {

    private String slotId;
    private String strategyId;
    private AdFormat adFormat;
    private long refreshInterval;
    private List<StrategyItem> strategies;

    public AdUnitConfig() {
        this.slotId = null;
        this.strategyId = "default";
        this.refreshInterval = 0L;
        this.strategies = new CopyOnWriteArrayList<>();
    }

    private AdUnitConfig(Builder builder) {
        this.slotId = builder.slotId;
        this.strategyId = builder.strategyId != null ? builder.strategyId : "default";
        this.adFormat = builder.adFormat;
        this.refreshInterval = builder.refreshInterval;
        this.strategies = builder.strategies != null ? new CopyOnWriteArrayList<>(builder.strategies) : new CopyOnWriteArrayList<>();
        // 构造时按 priority 升序排序一次, 保证 AdStrategyManager 拿到的就是有序链
        sortByPriority(this.strategies);
    }

    // ── Getters / Setters ──

    public String getSlotId() {
        return slotId;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public String getStrategyId() {
        return strategyId != null ? strategyId : "default";
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public AdFormat getAdFormat() {
        return adFormat;
    }

    public void setAdFormat(AdFormat adFormat) {
        this.adFormat = adFormat;
    }

    public long getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(long refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public List<StrategyItem> getStrategies() {
        return strategies != null ? strategies : Collections.emptyList();
    }

    public void setStrategies(List<StrategyItem> strategies) {
        this.strategies = strategies;
        sortByPriority(this.strategies);
    }

    // ── 便捷方法：单阶段查询（保持子策略 execute(AdUnitConfig) 签名不变） ──

    /**
     * 便捷方法：返回第一个策略阶段（即 strategies[0]）。无阶段时返回 null。
     * <p>主要用于子策略（{@code BiddingStrategy} / {@code WaterfallStrategy}）在单阶段场景下
     * 通过 {@code unitConfig.getFirstStage()} 直接拿到自己负责的 {@link StrategyItem}，
     * 内部再用 {@link StrategyItem#getSources()} / {@link StrategyItem#getTimeoutMs()}
     * / {@link StrategyItem#getType()} 取具体字段。</p>
     */
    public StrategyItem getFirstStage() {
        if (strategies == null || strategies.isEmpty()) return null;
        return strategies.get(0);
    }

    /**
     * 便捷方法：返回第一个策略阶段下的广告源列表（{@code strategies[0].getSources()}）。
     * <p>无任何阶段配置时返回空列表而非 null，方便子策略直接遍历。</p>
     */
    public List<AdSourceConfig> getSources() {
        StrategyItem first = getFirstStage();
        return first != null ? first.getSources() : Collections.emptyList();
    }

    /**
     * 便捷方法：返回第一个策略阶段的超时时间（{@code strategies[0].getTimeoutMs()}）。
     * <p>无任何阶段配置时返回默认值 <b>5000ms</b>。
     * （由 2000ms 调整为 5000ms，与 {@code StrategyItem} 默认值/业务方常见策略保持一致，
     * 避免在 {@code AdUnitConfig} 缺失阶段配置时被过度快地判 noFill。）</p>
     */
    public long getTimeoutMs() {
        StrategyItem first = getFirstStage();
        return first != null ? first.getTimeoutMs() : 5000L;
    }

    /**
     * 便捷方法：返回第一个策略阶段的策略类型（{@code strategies[0].getType()}）。
     * <p>无任何阶段配置时返回 {@link StrategyType#WATERFALL}。</p>
     */
    public StrategyType getStrategyType() {
        StrategyItem first = getFirstStage();
        return first != null ? first.getType() : StrategyType.WATERFALL;
    }

    /**
     * 便捷方法：是否包含多个策略阶段（{@code strategies.size() >= 2}）。
     * <p>{@code AdStrategyManager} 用此方法判断走 priority chain（{@link com.ima.union.core.strategy.HybridStrategy}）
     * 还是走单阶段子策略。</p>
     */
    public boolean isHybrid() {
        return strategies != null && strategies.size() >= 2;
    }

    /**
     * 获取该广告位下所有阶段（按 priority 升序）的全部广告源，扁平化为一个 List。
     * <p>主要给"展示后回查 adUnitId 对应 AdSourceConfig"这种场景使用（如上报 ecpm/bidToken）。
     * 注意：同名 adUnitId 配置在不同阶段时，<b>返回第一个匹配</b>（priority 最高的阶段）。</p>
     */
    public List<AdSourceConfig> getAllSourcesFlat() {
        List<AdSourceConfig> flat = new ArrayList<>();
        if (strategies == null) return flat;
        for (StrategyItem item : strategies) {
            if (item == null) continue;
            flat.addAll(item.getSources());
        }
        return flat;
    }

    /**
     * 按 {@code priority} 升序原地排序。priority 相等时保持插入顺序稳定（{@link Collections#sort}
     * 使用 TimSort，是稳定排序）。
     * <p>注意：{@link CopyOnWriteArrayList} 的 {@code ListIterator.set()} 会抛
     * {@link UnsupportedOperationException}，而 {@link Collections#sort} 内部依赖 {@code ListIterator}
     * 做原地交换，因此对 COW 列表需要先拷贝到 {@link ArrayList} 排序，再写回原容器。</p>
     *
     * <p>兼容说明：项目 {@code minSdk=21}、{@code sourceCompatibility=11}。使用传统匿名 {@code Comparator}
     * 实现而非 {@code Comparator.comparingInt(...)} / method reference，是为了避免在未启用
     * Java 8 desugaring 时遭遇 {@code NoClassDefFoundError}（lambda 字节码依赖 {@code java.lang.invoke.LambdaMetafactory}，
     * method reference 依赖 {@code java.lang.invoke.MethodHandle}，API 21-23 默认不打包）。</p>
     */
    private static void sortByPriority(List<StrategyItem> list) {
        if (list == null || list.size() < 2) return;
        Comparator<StrategyItem> byPriority = new Comparator<StrategyItem>() {
            @Override
            public int compare(StrategyItem a, StrategyItem b) {
                int pa = a != null ? a.getPriority() : 0;
                int pb = b != null ? b.getPriority() : 0;
                return Integer.compare(pa, pb);
            }
        };
        if (list instanceof CopyOnWriteArrayList) {
            List<StrategyItem> snapshot = new ArrayList<>(list);
            Collections.sort(snapshot, byPriority);
            list.clear();
            list.addAll(snapshot);
        } else {
            Collections.sort(list, byPriority);
        }
    }

    /**
     * 从 JSON 解析广告位配置。
     * <p>所有字段均可空，未配置时使用 {@link #AdUnitConfig()} 的默认值。</p>
     * 
     */
    public static AdUnitConfig fromJson(JSONObject json) throws JSONException {
        AdUnitConfig cfg = new AdUnitConfig();
        if (json == null) return cfg;
        if (json.has("slotId")) cfg.slotId = json.optString("slotId", null);
        if (json.has("strategyId")) cfg.strategyId = json.optString("strategyId", "default");
        if (json.has("adFormat")) {
            String fmt = json.optString("adFormat", null);
            if (fmt != null && !fmt.isEmpty()) cfg.adFormat = AdFormat.valueOf(fmt);
        }
        if (json.has("refreshInterval")) cfg.refreshInterval = json.optLong("refreshInterval", 0);
        if (json.has("strategies")) {
            JSONArray arr = json.optJSONArray("strategies");
            if (arr != null) {
                List<StrategyItem> list = new CopyOnWriteArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(StrategyItem.fromJson(arr.getJSONObject(i)));
                }
                cfg.strategies = list;
                sortByPriority(cfg.strategies);
            }
        }
        return cfg;
    }

    // ── Builder ──

    public static class Builder {
        private String slotId;
        private String strategyId;
        private AdFormat adFormat;
        private long refreshInterval;
        private List<StrategyItem> strategies;

        public Builder slotId(String slotId) {
            this.slotId = slotId;
            return this;
        }

        public Builder strategyId(String strategyId) {
            this.strategyId = strategyId;
            return this;
        }

        public Builder adFormat(AdFormat adFormat) {
            this.adFormat = adFormat;
            return this;
        }

        public Builder refreshInterval(long refreshInterval) {
            this.refreshInterval = refreshInterval;
            return this;
        }

        public Builder strategies(List<StrategyItem> strategies) {
            this.strategies = strategies;
            return this;
        }

        /**
         * 从已有 AdUnitConfig 复制所有字段到当前 Builder。
         * 用于需要覆盖单个字段的场景（如运行时增加/替换某个策略阶段）。
         */
        public Builder fromCopy(AdUnitConfig original) {
            if (original == null) return this;
            this.slotId = original.getSlotId();
            this.strategyId = original.getStrategyId();
            this.adFormat = original.getAdFormat();
            this.refreshInterval = original.getRefreshInterval();
            this.strategies = original.getStrategies() != null
                    ? new CopyOnWriteArrayList<>(original.getStrategies())
                    : null;
            return this;
        }

        public AdUnitConfig build() {
            return new AdUnitConfig(this);
        }
    }

    @Override
    public String toString() {
        int strategyCount = strategies == null ? 0 : strategies.size();
        int sourceCount = 0;
        if (strategies != null) {
            for (StrategyItem item : strategies) {
                if (item != null) sourceCount += item.getSources().size();
            }
        }
        return "AdUnitConfig{slotId='" + slotId
                + "', strategyId='" + getStrategyId()
                + "', adFormat=" + adFormat
                + ", strategies=" + strategyCount
                + ", sources=" + sourceCount + "}";
    }
}
