package com.ima.union.core.strategy;

import android.content.Context;

import com.ima.union.core.concurrent.ExecutorManager;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdUnitConfig;
import com.ima.union.core.model.StrategyItem;
import com.ima.union.core.model.StrategyType;
import com.ima.union.utils.FsLogger;

import java.util.List;

/**
 * 策略执行器（单例）。
 *
 * <p>每次 {@link #execute} 只跑一条 {@link AdUnitConfig} 配置，
 * 多次同 slotId 的请求完全独立、互不影响，业务方各自通过 callback 接收结果。</p>
 *
 * <p>策略路由：混合策略（priority chain）通过 {@link AdUnitConfig#isHybrid()} 隐式判断
 * （{@code strategies.size() >= 2}），单阶段按 {@link StrategyItem#getType()} 路由。</p>
 */
public class AdStrategyManager {

    private static final AdStrategyManager INSTANCE = new AdStrategyManager();

    private AdStrategyManager() {}

    public static AdStrategyManager getInstance() { return INSTANCE; }

    public void execute(Context context, AdRequestParams params, AdUnitConfig unitConfig, AdStrategyCallback callback) {
        String requestSlotId = params.getSlotId();
        String configSlotId = unitConfig != null ? unitConfig.getSlotId() : null;
        if (requestSlotId == null || requestSlotId.isEmpty()
                || configSlotId == null || configSlotId.isEmpty()
                || !requestSlotId.equals(configSlotId)) {
            FsLogger.e("AdStrategyManager", "slotId mismatch: request=" + requestSlotId
                    + " config=" + configSlotId + ", refuse to execute");
            if (callback != null) {
                final String reason = "slotId mismatch: request=" + requestSlotId
                        + " config=" + configSlotId;
                ExecutorManager.postToMain(() -> callback.onNoFill(reason));
            }
            return;
        }

        AdStrategy strategy = selectStrategy(unitConfig);
        logExecuteInfo(params, unitConfig, strategy);
        strategy.execute(context, params, unitConfig, callback);
    }

    /**
     * 根据 {@link AdUnitConfig#getStrategies()} 选择具体 strategy。
     */
    private AdStrategy selectStrategy(AdUnitConfig unitConfig) {
        List<StrategyItem> stages = unitConfig.getStrategies();

        // 无任何策略阶段: 退化为空 WaterfallStrategy, 立即 noFill
        if (stages == null || stages.isEmpty()) {
            return new WaterfallStrategy();
        }

        // 多阶段（strategies.size() >= 2）→ priority chain
        if (stages.size() >= 2) {
            return new HybridStrategy();
        }

        // 单阶段: 根据 type 路由
        StrategyItem first = stages.get(0);
        StrategyType type = first != null ? first.getType() : StrategyType.WATERFALL;
        if (type == StrategyType.BIDDING) {
            return new BiddingStrategy();
        }
        return new WaterfallStrategy();
    }

    /**
     * 打印请求入口日志：slotId + 阶段数 + 整链类型描述 + sources 数量 + format。
     */
    private void logExecuteInfo(AdRequestParams params, AdUnitConfig unitConfig, AdStrategy strategy) {
        List<StrategyItem> stages = unitConfig.getStrategies();
        int stageCount = stages == null ? 0 : stages.size();
        int sourceCount = unitConfig.getAllSourcesFlat().size();

        String strategyDesc;
        if (stages == null || stages.isEmpty()) {
            strategyDesc = "EMPTY";
        } else if (stages.size() == 1) {
            StrategyItem s = stages.get(0);
            strategyDesc = String.valueOf(s.getType());
        } else {
            StringBuilder sb = new StringBuilder("CHAIN[");
            for (int i = 0; i < stages.size(); i++) {
                if (i > 0) sb.append("→");
                StrategyItem s = stages.get(i);
                sb.append(s.getPriority()).append(":").append(s.getType());
            }
            sb.append("]");
            strategyDesc = sb.toString();
        }

        FsLogger.d("AdStrategyManager", "▶ execute: slotId=" + params.getSlotId()
                + " strategy=" + strategyDesc
                + " stages=" + stageCount
                + " format=" + unitConfig.getAdFormat()
                + " sources=" + sourceCount
                + " impl=" + strategy.getClass().getSimpleName());
    }
}
