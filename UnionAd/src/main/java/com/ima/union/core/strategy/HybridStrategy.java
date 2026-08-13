package com.ima.union.core.strategy;

import android.content.Context;
import com.ima.union.utils.FsLogger;


import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.AdUnitConfig;
import com.ima.union.core.model.StrategyItem;
import com.ima.union.core.model.StrategyType;
import com.ima.union.core.model.UnionAdResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 优先级链式策略（Priority Chain，原 HybridStrategy）。
 *
 * 
 *
 * <p><b>阶段间 sources 隔离</b>：每个阶段的 sources 完全独立（同一 SDK 可在不同阶段配置不同 adUnitId）。
 * 传给子策略前用 {@link AdUnitConfig.Builder#fromCopy(AdUnitConfig) fromCopy} + 覆盖 {@code strategies}
 * 构造一个"单阶段虚拟 unitConfig"，避免子策略读到其它阶段的数据。</p>
 *
 * <p><b>线程安全</b>：
 * <ul>
 *   <li>每次 {@code execute()} 都会构造新的 {@link BiddingStrategy} / {@code WaterfallStrategy} 实例，
 *       链路上每个阶段独立</li>
 *   <li>当前活跃子策略保存在 {@link #activeSubStrategy}，外部 {@link #cancel()} 时转发 cancel 给子策略</li>
 * </ul></p>
 */
public class HybridStrategy extends BaseStrategy {

    private static final String TAG = "HybridStrategy";

    /**
     * 当前活跃的子策略实例，用于外部 cancel 转发。
     * CopyOnWriteArrayList 避免 cancel/select 跨线程写竞态。
     */
    private final List<AdStrategy> activeSubStrategy = new CopyOnWriteArrayList<>();

    public HybridStrategy() {
        super(TAG);
    }

    /**
     * 本策略对应 {@link StrategyType#HYBRID} 枚举项，恢复显式标记以便业务方打点/日志
     * 区分单阶段 vs 链式。
     * <p>{@link AdStrategyManager} 通过 {@link AdUnitConfig#isHybrid()} 路由到本类，
     * {@link #getStrategyType()} 仅用于运行期打点/日志等只读场景。</p>
     */
    @Override
    public StrategyType getStrategyType() {
        return StrategyType.HYBRID;
    }

    @Override
    public void execute(Context context, AdRequestParams params, AdUnitConfig unitConfig, AdStrategyCallback callback) {
        resetFlags();

        final List<StrategyItem> strategies = unitConfig.getStrategies();
        if (strategies == null || strategies.isEmpty()) {
            FsLogger.w("HybridStrategy", "Hybrid execute with no strategies, slotId=" + params.getSlotId());
            notifyNoFill(callback, "No strategies configured for " + params.getSlotId());
            return;
        }

        FsLogger.d("HybridStrategy", "▶ Hybrid chain start: slotId=" + params.getSlotId()
                + " stages=" + strategies.size()
                + " stages=" + describeStages(strategies));

        // 入口前 cancel 判断（schedule 异步场景的兜底, 实际几乎不可能命中）
        if (cancelFlipped.get()) {
            FsLogger.d("HybridStrategy", "Hybrid cancelled before start");
            return;
        }

        // 从 priority=1 的阶段开始 chain 执行
        executeStage(context, params, unitConfig, strategies, 0, callback);
    }

    /**
     * 递归执行第 {@code stageIndex} 个阶段。失败（{@code onNoFill}）→ 推进到下一阶段；成功（{@code onAdLoaded}）
     * → 整条链终止。
     */
    private void executeStage(Context context, AdRequestParams params, AdUnitConfig unitConfig,
                              List<StrategyItem> strategies, int stageIndex,
                              AdStrategyCallback callback) {
        if (cancelFlipped.get()) {
            FsLogger.d("HybridStrategy", "Hybrid cancelled at stage " + stageIndex);
            return;
        }

        // 链尾：所有阶段都已 noFill，整条链结束
        if (stageIndex >= strategies.size()) {
            FsLogger.w("HybridStrategy", "Hybrid chain exhausted: all stages noFill, slotId=" + params.getSlotId());
            notifyNoFill(callback, "All strategies noFill for " + params.getSlotId());
            return;
        }

        final StrategyItem stage = strategies.get(stageIndex);
        if (stage == null) {
            FsLogger.w("HybridStrategy", "Skip null stage at index " + stageIndex);
            executeStage(context, params, unitConfig, strategies, stageIndex + 1, callback);
            return;
        }

        // 该阶段没有启用的 source → 跳过, 直接进下一阶段
        if (!hasEnabledSource(stage.getSources())) {
            FsLogger.d("HybridStrategy", "Stage " + stageIndex + " (priority=" + stage.getPriority()
                    + " type=" + stage.getType() + ") has no enabled source, skip");
            executeStage(context, params, unitConfig, strategies, stageIndex + 1, callback);
            return;
        }

        // 构造只包含当前阶段的"虚拟 unitConfig"，传给对应 type 的子策略
        // 子策略通过 AdUnitConfig.getSources()/getTimeoutMs()/getStrategyType() 自动拿到本阶段数据
        AdUnitConfig stageConfig = new AdUnitConfig.Builder()
                .fromCopy(unitConfig)
                .strategies(wrapSingleStage(stage))
                .build();

        AdStrategy subStrategy = createSubStrategy(stage);
        if (subStrategy == null) {
            FsLogger.w("HybridStrategy", "Unknown strategy type: " + stage.getType() + ", skip stage " + stageIndex);
            executeStage(context, params, unitConfig, strategies, stageIndex + 1, callback);
            return;
        }
        activeSubStrategy.add(subStrategy);

        FsLogger.d("HybridStrategy", "▶ Execute stage " + stageIndex + ": priority=" + stage.getPriority()
                + " type=" + stage.getType()
                + " timeoutMs=" + stage.getTimeoutMs() + "ms"
                + " sources=" + countEnabledSources(stage.getSources()));

        // 包装子回调：成功 → 终止链；失败 → 推进到下一阶段
        final AdStrategy outerCallback = subStrategy;
        final int currentStageIndex = stageIndex;
        subStrategy.execute(context, params, stageConfig, new AdStrategyCallback() {
            @Override
            public void onAdLoaded(UnionAdResponse response) {
                adLoadedFlipped.set(true);
                activeSubStrategy.remove(outerCallback);
                FsLogger.d("HybridStrategy", "Stage " + currentStageIndex + " loaded: sdkName=" + response.getSdkName()
                        + " ecpm=" + response.getEcpm());
                notifyAdLoaded(callback, response);
            }

            @Override
            public void onNoFill(String reason) {
                activeSubStrategy.remove(outerCallback);
                if (cancelFlipped.get()) {
                    FsLogger.d("HybridStrategy", "Hybrid cancelled, skip next stage");
                    return;
                }
                if (adLoadedFlipped.get()) {
                    // 上一阶段 onAdLoaded 已经先触发, noFill 丢弃
                    FsLogger.d("HybridStrategy", "Hybrid adLoaded already fired, ignore stage " + currentStageIndex + " noFill");
                    return;
                }
                FsLogger.d("HybridStrategy", "Stage " + currentStageIndex + " noFill: " + reason
                        + ", fallback to next stage");
                executeStage(context, params, unitConfig, strategies, currentStageIndex + 1, callback);
            }
        });
    }

    /**
     * 把单个 stage 包成只含一个元素的 strategies 列表，给 {@link AdUnitConfig.Builder#strategies} 用。
     */
    private List<StrategyItem> wrapSingleStage(StrategyItem stage) {
        List<StrategyItem> list = new ArrayList<>(1);
        list.add(stage);
        return list;
    }

    /**
     * 根据 stage.type 实例化对应子策略。
     * <p>无对应 type（如 {@link StrategyType#HYBRID} 不能再次嵌套）返回 null，调用方负责跳过。</p>
     */
    private AdStrategy createSubStrategy(StrategyItem stage) {
        StrategyType type = stage.getType();
        if (type == null) return null;
        switch (type) {
            case BIDDING:
                return new BiddingStrategy();
            case WATERFALL:
                return new WaterfallStrategy();
            case HYBRID:
                // 嵌套混合策略无意义,跳过
            default:
                return null;
        }
    }

    /**
     * 判断该阶段是否至少有一个启用的 source。
     */
    private boolean hasEnabledSource(List<AdSourceConfig> sources) {
        if (sources == null) return false;
        for (AdSourceConfig src : sources) {
            if (src != null && src.isEnabled()) return true;
        }
        return false;
    }

    /**
     * 统计启用的 source 数量，用于日志。
     */
    private int countEnabledSources(List<AdSourceConfig> sources) {
        if (sources == null) return 0;
        int n = 0;
        for (AdSourceConfig src : sources) {
            if (src != null && src.isEnabled()) n++;
        }
        return n;
    }

    /**
     * 用一个简短字符串描述整个阶段链（供日志/调试用）。
     */
    private String describeStages(List<StrategyItem> strategies) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < strategies.size(); i++) {
            if (i > 0) sb.append(" → ");
            StrategyItem s = strategies.get(i);
            sb.append(s.getPriority()).append(':').append(s.getType());
            if (s.getTimeoutMs() > 0) {
                sb.append('(').append(s.getTimeoutMs()).append("ms)");
            }
        }
        return sb.append(']').toString();
    }

    /**
     * cancel 转发：BaseStrategy 置标志位后，遍历当前活跃子策略也调用 cancel()，
     * 让在途的请求/超时调度能立即退出。
     */
    @Override
    public void cancel() {
        super.cancel();
        for (AdStrategy sub : activeSubStrategy) {
            try {
                sub.cancel();
            } catch (Exception e) {
                FsLogger.w("HybridStrategy", "Sub-strategy cancel failed: " + sub.getClass().getSimpleName(), e);
            }
        }
        activeSubStrategy.clear();
    }
}
