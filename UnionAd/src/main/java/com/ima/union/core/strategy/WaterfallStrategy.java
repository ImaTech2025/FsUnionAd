package com.ima.union.core.strategy;

import android.content.Context;
import com.ima.union.utils.FsLogger;


import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.adapter.AdInitListener;
import com.ima.union.core.concurrent.ExecutorManager;
import com.ima.union.core.model.AdLoadResult;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.AdUnitConfig;
import com.ima.union.core.model.StrategyType;
import com.ima.union.core.model.UnionAdResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 瀑布流策略（Waterfall）：
 * 
 * <ul>
 *   <li><b>宏观 {@code AdUnitConfig.timeoutMs}</b>：在 {@link #execute} 入口由
 *       {@code scheduledExecutor.schedule(...)} 调度整段链路超时任务；到点时
 *       {@code timeoutFlipped.set(true)}，并按"是否已有广告召回"决定是否回调
 *       {@code onNoFill("Waterfall global timeout ...")}</li>
 *   <li><b>微观 {@code AdSourceConfig.timeout}</b>：由各 Adapter 内部用
 *       {@link com.ima.union.core.adapter.AdLoadTimeout} 兜底（PangleAdAdapter /
 *       FissionAdAdapter），或透传给平台 SDK（BaiduAdAdapter 用
 *       {@code SplashAd.KEY_TIMEOUT}），GdtAdAdapter 自身有模拟回调
 *       不需要兜底。超时后会以 {@code onLoadFailed(sdkName, FsAdErrorCode.SDK_LOAD_TIMEOUT, "...")}
 *       形式回调到 {@link #onLoadResult}，统一走 Failure 分支推进 tryNextSource</li>
 *   <li>串行链路在每源请求前/后做两次 {@code timeoutFlipped.get()} 判断：
 *       请求前超时则不再进入请求，请求后超时则丢弃当前结果（超时分支已经回调）</li>
 * </ul>
 * 
 *
 * <p><b>线程安全说明</b>：
 * <ul>
 *   <li>每次 {@code execute()} 都是一次独立任务，线程池由 {@link ExecutorManager} 统一管理,
 *       同一任务内的所有逻辑均在单一线程串行执行</li>
 *   <li>4 个标志位（{@code cancelled} / {@code cancelFlipped} / {@code timeoutFlipped} /
 *       {@code adLoadedFlipped}）由 {@link BaseStrategy} 统一持有, 用 {@code AtomicBoolean} 跨线程可见</li>
 *   <li><b>回调驱动式</b>：对每个源先调 {@code AdAdapterRegistry.ensureInitializedAsync}，
 *       listener 触发后用 {@code executor.execute} 把后续 doLoad/tryNextSource 投递回
 *       瀑布流单线程，保证异步初始化回调的"下一动作"仍然在串行线程中执行</li>
 * </ul></p>
 */
public class WaterfallStrategy extends BaseStrategy {

    private static final String TAG = "WaterfallStrategy";

    private final ExecutorService executor;

    public WaterfallStrategy() {
        super(TAG);
        this.executor = ExecutorManager.get(ExecutorManager.PoolType.STRATEGY_LOAD);
    }

    @Override
    public StrategyType getStrategyType() {
        return StrategyType.WATERFALL;
    }

    @Override
    public void execute(Context context, AdRequestParams params, AdUnitConfig unitConfig, AdStrategyCallback callback) {
        // 每次执行前重置所有标志位
        resetFlags();

        final long timeoutMs = unitConfig.getTimeoutMs();
        FsLogger.d("WaterfallStrategy", "Waterfall start for " + params.getSlotId()
                + ", timeoutMs=" + timeoutMs + "ms");

        // ========== Phase 1: 调度宏观超时任务 ==========
        // C4 修复：超时任务与成功分支都用 CAS 抢 adLoadedFlipped 回调令牌，互斥回调，
        // 避免 TOCTOU 竞态导致业务方同时收到 onNoFill 与 onAdLoaded
        ScheduledFuture<?> timeoutFuture = scheduledExecutor.schedule(() -> {
            if (cancelFlipped.get()) {
                return;
            }
            // CAS 抢 timeoutFlipped 令牌
            if (!timeoutFlipped.compareAndSet(false, true)) {
                return;
            }
            // 抢到 timeoutFlipped 后，再 CAS 抢 adLoadedFlipped 回调令牌（与成功分支互斥）
            if (!adLoadedFlipped.compareAndSet(false, true)) {
                // 成功分支已抢到回调令牌，不重复回调
                FsLogger.d("WaterfallStrategy", "Waterfall timeout but ad already loaded, skip noFill");
                return;
            }
            FsLogger.w("WaterfallStrategy", "Waterfall global timeout reached (" + timeoutMs + "ms), no ad loaded");
            notifyNoFill(callback, "Waterfall global timeout for " + params.getSlotId());
        }, timeoutMs, TimeUnit.MILLISECONDS);

        executor.execute(() -> {
            // ========== Phase 2: 收集并排序源 ==========
            List<AdSourceConfig> sortedSources = collectAndSortSources(unitConfig);
            if (sortedSources.isEmpty()) {
                timeoutFuture.cancel(false);
                notifyNoFill(callback, "No sources available for " + params.getSlotId());
                return;
            }

            logWaterfallSources(unitConfig, sortedSources);

            // ========== Phase 3: 回调驱动式依次尝试加载 ==========
            tryNextSource(context, params, unitConfig, sortedSources, 0, callback, timeoutFuture);
        });
    }

    /**
     * 收集 enabled=true 的源，并按 priority 升序排序（priority 数值越小优先级越高）。
     */
    private List<AdSourceConfig> collectAndSortSources(AdUnitConfig unitConfig) {
        List<AdSourceConfig> sources = new ArrayList<>();
        for (AdSourceConfig src : unitConfig.getSources()) {
            src.setAdFormat(unitConfig.getAdFormat());
            if (src.isEnabled()) {
                sources.add(src);
            }
        }
        Collections.sort(sources, Comparator.comparingInt(AdSourceConfig::getPriority));
        return sources;
    }

    /**
     * 打印瀑布流源列表，便于调试。
     */
    private void logWaterfallSources(AdUnitConfig unitConfig, List<AdSourceConfig> sortedSources) {
        for (int i = 0; i < sortedSources.size(); i++) {
            AdSourceConfig s = sortedSources.get(i);
            FsLogger.d("WaterfallStrategy", "  [#" + (i + 1) + "] sdkName=" + s.getSdkName()
                    + " sdkType=" + s.getSdkType().getSdkName()
                    + " priority=" + s.getPriority()
                    + " bidFloor=" + s.getBidFloor()
                    + " perSourceTimeout=" + s.getTimeout() + "ms");
        }
    }

    /**
     * 处理第 {@code index} 个源：异步初始化平台 SDK，初始化成功后执行 loadAd，
     * 初始化失败/loadAd 失败都递归推进到下一个源。整个流程是回调驱动的。
     *
     * <p><b>请求前超时判断</b>：进入函数先看 {@code timeoutFlipped} 是否已置位；
     * 若是则直接 cancel 超时任务（不需要再等）并跳过剩余所有源（noFill 已由超时任务触发）。</p>
     */
    private void tryNextSource(Context context, AdRequestParams params, AdUnitConfig unitConfig,
                               List<AdSourceConfig> sortedSources,
                               int index, AdStrategyCallback callback,
                               ScheduledFuture<?> timeoutFuture) {
        if (cancelFlipped.get()) {
            FsLogger.d("WaterfallStrategy", "Waterfall cancelled, stop trying");
            return;
        }

        // 请求前超时判断：宏观 timeoutMs 已到点，noFill 已经被超时任务回调，直接停止遍历
        if (timeoutFlipped.get()) {
            FsLogger.d("WaterfallStrategy", "Waterfall timeout flipped before source " + index + ", stop trying");
            return;
        }

        if (index >= sortedSources.size()) {
            // 全部源都尝试完毕，无填充
            timeoutFuture.cancel(false);
            notifyNoFill(callback, "All sources failed for " + params.getSlotId());
            return;
        }

        AdSourceConfig sourceConfig = sortedSources.get(index);
        AdAdapter adapter = StrategyUtils.resolveAdapter(sourceConfig);
        if (adapter == null) {
            FsLogger.w("WaterfallStrategy", "No adapter for source " + sourceConfig.getSdkName() + ", skip");
            tryNextSource(context, params, unitConfig, sortedSources, index + 1, callback, timeoutFuture);
            return;
        }

        AdAdapterRegistry.getInstance().ensureInitializedAsync(
                context, adapter, sourceConfig.getAppId(), sourceConfig.getToken(),
                new AdInitListener() {
                    @Override
                    public void onInitSuccess(AdAdapter a) {
                        // 投递回瀑布流单线程，保持原有串行控制流
                        executor.execute(() -> doLoad(context, params, unitConfig, sortedSources, index,
                                sourceConfig, adapter, callback, timeoutFuture));
                    }

                    @Override
                    public void onInitFailure(AdAdapter a, int code, String msg) {
                        FsLogger.w("WaterfallStrategy", "Waterfall skip source " + sourceConfig.getSdkName()
                                + " due to init failure: [" + code + "] " + msg);
                        executor.execute(() -> tryNextSource(context, params, unitConfig, sortedSources,
                                index + 1, callback, timeoutFuture));
                    }
                });
    }

    /**
     * 当前源已成功初始化，发起 loadAd。成功即回调上层，失败推进到下一个源。
     *
     * 
     */
    private void doLoad(Context context, AdRequestParams params, AdUnitConfig unitConfig,
                        List<AdSourceConfig> sortedSources, int index,
                        AdSourceConfig sourceConfig, AdAdapter adapter,
                        AdStrategyCallback callback, ScheduledFuture<?> timeoutFuture) {
        if (cancelFlipped.get()) {
            return;
        }

        FsLogger.d("WaterfallStrategy", "Waterfall requesting: " + sourceConfig.getSdkName()
                + " (" + sourceConfig.getSdkType().getSdkName() + ")"
                + " perSourceTimeout=" + sourceConfig.getTimeout() + "ms");

        // 发起异步请求（不阻塞），结果在 onLoadResult 内推进
        StrategyUtils.requestWithTimeout(
                context, adapter, params, sourceConfig,
                result -> onLoadResult(context, params, unitConfig, sortedSources, index,
                        sourceConfig, result, callback, timeoutFuture),
                TAG);
    }

    /**
     * 异步请求的统一回调。处理"请求后超时判断"和"成功/失败推进下一源"逻辑。
     */
    private void onLoadResult(Context context, AdRequestParams params, AdUnitConfig unitConfig,
                              List<AdSourceConfig> sortedSources, int index,
                              AdSourceConfig sourceConfig, AdLoadResult result,
                              AdStrategyCallback callback, ScheduledFuture<?> timeoutFuture) {
        if (cancelFlipped.get()) {
            return;
        }

        // 请求后超时判断：宏观 timeoutMs 在请求期间已到点，丢弃当前结果（noFill 已回调）
        if (timeoutFlipped.get()) {
            FsLogger.w("WaterfallStrategy", "Waterfall timeout flipped during request of " + sourceConfig.getSdkName()
                    + ", drop result");
            return;
        }

        if (result instanceof AdLoadResult.Success) {
            UnionAdResponse response = ((AdLoadResult.Success) result).getResponse();
            // C4 修复：CAS 抢 adLoadedFlipped 回调令牌，避免与超时任务 TOCTOU 竞态导致双重回调
            if (!adLoadedFlipped.compareAndSet(false, true)) {
                // 已被超时任务或 cancel 抢到回调令牌
                FsLogger.d("WaterfallStrategy", "Waterfall ad loaded but result already notified, skip");
                return;
            }
            timeoutFuture.cancel(false);
            FsLogger.d("WaterfallStrategy", "Waterfall success: " + sourceConfig.getSdkName());
            notifyAdLoaded(callback, response);
        } else if (result instanceof AdLoadResult.Failure) {
            AdLoadResult.Failure failure = (AdLoadResult.Failure) result;
            FsLogger.w("WaterfallStrategy", "Waterfall failed: " + sourceConfig.getSdkName()
                    + " [" + failure.getErrorCode() + "] " + failure.getErrorMsg()
                    + ", try next");
            tryNextSource(context, params, unitConfig, sortedSources, index + 1, callback, timeoutFuture);
        }
    }
}
