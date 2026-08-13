package com.ima.union.core.strategy;

import android.content.Context;
import com.ima.union.utils.FsLogger;


import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdInitListener;
import com.ima.union.core.cache.BidFailCacheManager;
import com.ima.union.core.concurrent.ExecutorManager;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.AdUnitConfig;
import com.ima.union.core.model.BidLossReason;
import com.ima.union.core.model.StrategyType;
import com.ima.union.core.model.UnionAdResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 实时竞价策略（Bidding）：
 * 
 *
 * <p><b>线程安全说明</b>：
 * <ul>
 *   <li>每次 {@code execute()} 都是一次独立任务, 4 个标志位（{@code cancelled} / {@code cancelFlipped} /
 *       {@code timeoutFlipped} / {@code adLoadedFlipped}）由 {@link BaseStrategy} 持有</li>
 *   <li>{@code bidResponses} 用 {@code CopyOnWriteArrayList}, 并发写入安全</li>
 *   <li>{@code remainingBids} 用 {@link AtomicInteger} CAS 递减；归零时投递回调推进 {@link #doExecute} 的
 *       Phase 3（loadFromSortedBids）</li>
 *   <li>每个 bidder 内部：先 {@code AdAdapterRegistry.ensureInitializedAsync}，listener 触发后
 *       {@code executor.execute(doBid)} —— 全程通过回调推进，不在业务线程阻塞</li>
 * </ul></p>
 */
public class BiddingStrategy extends BaseStrategy {

    private static final String TAG = "BiddingStrategy";

    private final ExecutorService executor;
    /**
     * 剩余未完成的 bidder 数。归零时通过 {@link #executor} 推进 Phase 3；
     * 超时调度和 onCancel 会强制 set 为 0（也意味着"全部已完成"语义），触发提前推进。
     * <p>无 {@code CountDownLatch.await} 阻塞：所有等待都在事件驱动中完成。</p>
     */
    private final AtomicInteger remainingBids = new AtomicInteger(0);

    /**
     * 当前执行任务的 {@link BiddingContext} 引用。
     * <p>让超时调度 lambda 闭包能拿到 {@code performParallelBidding}
     * 中已建好的 ctx（含 {@code bidResponses}），从而在 Case 1（超时但已有有效报价）
     * 时能拿到当前最高价继续走 Phase 3，而不是直接 {@code notifyNoFill} 丢所有报价。</p>
     * <p>每次 {@code execute()} 入口置 null；{@code performParallelBidding} 构造完 ctx 后置入；
     * 如果超时调度到点时 ctx 还没建好（doExecute 还没跑到 Phase 2），走兜底 noFill 路径。</p>
     */
    private final AtomicReference<BiddingContext> ctxRef = new AtomicReference<>(null);

    /**
     * 并行竞价阶段共享上下文。让 doExecute 内嵌套的多处调用（performSingleBid / doBid /
     * continueAfterBids）能直接访问同一份 biddingSources / bidResponses 引用，
     * 避免层层传参的样板代码。
     */
    private static final class BiddingContext {
        final List<AdSourceConfig> biddingSources;
        final List<UnionAdResponse> bidResponses;
        final AdStrategyCallback callback;
        final ScheduledFuture<?> timeoutFuture;
        final Context context;
        final AdRequestParams params;
        final AdUnitConfig unitConfig;
        /**
         * Phase 3 启动权 CAS 标志：保证"进入 loadFromSortedBids 加载阶段"这件事
         * 全链路只发生一次。无论触发源是"最后一个 bidder 正常完成"还是"超时调度
         * 抢到令牌"，都通过 {@code phase3Started.compareAndSet(false, true)} 抢一次，
         * 抢到者才有权走 Phase 3；抢不到者直接退出（避免双 Phase 3 浪费）。
         */
        final AtomicBoolean phase3Started = new AtomicBoolean(false);

        BiddingContext(Context context, AdRequestParams params, AdUnitConfig unitConfig,
                       List<AdSourceConfig> biddingSources, List<UnionAdResponse> bidResponses,
                       AdStrategyCallback callback, ScheduledFuture<?> timeoutFuture) {
            this.context = context;
            this.params = params;
            this.unitConfig = unitConfig;
            this.biddingSources = biddingSources;
            this.bidResponses = bidResponses;
            this.callback = callback;
            this.timeoutFuture = timeoutFuture;
        }
    }

    public BiddingStrategy() {
        super(TAG);
        this.executor = ExecutorManager.get(ExecutorManager.PoolType.BIDDING);
    }

    @Override
    public StrategyType getStrategyType() {
        return StrategyType.BIDDING;
    }

    @Override
    public void execute(Context context, AdRequestParams params, AdUnitConfig unitConfig, AdStrategyCallback callback) {
        // 每次执行前重置所有标志位
        resetFlags();
        remainingBids.set(0);
        ctxRef.set(null);

        final long timeoutMs = unitConfig.getTimeoutMs();
        FsLogger.d("BiddingStrategy", "Starting bidding for " + params.getSlotId() + ", timeoutMs=" + timeoutMs + "ms");

        // ========== 调度宏观超时任务 ==========
        // 到点 → CAS 抢令牌 timeoutFlipped：
        //   - 抢到者：从 ctxRef 拿当前 ctx；forceFinishBids 让在飞 bidder 安全放行；
        //     投递 continueAfterBids 走统一入口。
        //     · Case 1（ctx 已建好，bidResponses 有数据）→ 拿当前最高价继续 Phase 3
        //     · Case 1'（ctx 已建好但 bidResponses 为空）→ noFill
        //     · Case 1''（ctx 还没建好：doExecute 没跑到 Phase 2）→ 兜底 noFill
        //   - 抢不到：说明已被 cancel / 已加载成功 / 已被正常完成路径置位（Case 2），
        //     静默退出，不再重复回调
        // 这样从根上保证"全链路只有一个线程负责回调结果给业务方"
        ScheduledFuture<?> timeoutFuture = scheduledExecutor.schedule(() -> {
            if (!timeoutFlipped.compareAndSet(false, true)) {
                // 已被别人置位（cancel / adLoaded / 正常完成路径）→ 静默退出
                return;
            }
            // 抢到令牌 → 强制归零 remainingBids，让"还在飞的 bidder 完成路径"安全放行（prev<=0 不再投递推进）
            forceFinishBids();
            // 尝试拿当前 ctx；拿不到说明 doExecute 还没进 Phase 2（极少见但可能）
            BiddingContext ctx = ctxRef.get();
            if (ctx == null) {
                // ctx 还没建好 → 兜底 noFill（这种情况下不可能有 bidResponses，所以不丢报价）
                FsLogger.w("BiddingStrategy", "Bidding global timeout reached (" + timeoutMs + "ms) before parallel bidding started, fallback noFill");
                notifyNoFill(callback, "Bidding global timeout (no parallel bidding started) for " + params.getSlotId());
                return;
            }
            // ctx 已建好 → 对已收集的报价上报 CHAIN_TIMEOUT，然后走统一推进入口
            FsLogger.w("BiddingStrategy", "Bidding global timeout reached (" + timeoutMs + "ms), handing over to unified continue path (current bidResponses=" + ctx.bidResponses.size() + ")");
            for (UnionAdResponse bid : ctx.bidResponses) {
                AdAdapter adapter = bid.getAdapter();
                if (adapter != null) {
                    adapter.reportBidFail(bid.getNativeAd(), BidLossReason.CHAIN_TIMEOUT);
                }
            }
            executor.execute(() -> continueAfterBids(ctx));
        }, timeoutMs, TimeUnit.MILLISECONDS);

        executor.execute(() -> doExecute(context, params, unitConfig, callback, timeoutFuture));
    }

    /**
     * 主执行流程：拆分为 收集源 → 并行竞价 → 排序加载 三个阶段，结构清晰。
     * <p>非阻塞：Phase 2 启动后立即返回；Phase 3 在所有 bidder 完成 / 超时 / cancel 时
     * 通过 {@link #continueAfterBids} 回调推进。</p>
     */
    private void doExecute(Context context, AdRequestParams params, AdUnitConfig unitConfig, AdStrategyCallback callback, ScheduledFuture<?> timeoutFuture) {
        // 入口保护：若已被 cancel/超时（极少见，但 executor 调度 + 宏观超时调度是并行任务，
        // 偶尔 doExecute 还没跑、超时已触发），不再推进 Phase 2。
        if (cancelFlipped.get() || timeoutFlipped.get()) {
            FsLogger.d("BiddingStrategy", "doExecute skipped: already cancelled or timed out");
            return;
        }

        // ===== Phase 1: 收集所有启用的竞价源 =====
        List<AdSourceConfig> biddingSources = collectEnabledSources(unitConfig);
        if (biddingSources.isEmpty()) {
            timeoutFuture.cancel(false);
            notifyNoFill(callback, "No bidding sources available for " + params.getSlotId());
            return;
        }

        // ===== Phase 2: 启动并行竞价（非阻塞） =====
        // 投递后立即返回，Phase 3 由 continueAfterBids 回调推进
        performParallelBidding(context, params, unitConfig, biddingSources, callback, timeoutFuture);
        // doExecute 结束 —— 后续推进由 executor 调度
    }

    /**
     * 从 unitConfig 中筛选出 enabled=true 的广告源。
     */
    private List<AdSourceConfig> collectEnabledSources(AdUnitConfig unitConfig) {
        List<AdSourceConfig> sources = new ArrayList<>();
        for (AdSourceConfig src : unitConfig.getSources()) {
            src.setAdFormat(unitConfig.getAdFormat());
            if (src.isEnabled()) {
                sources.add(src);
            }
        }
        return sources;
    }

    /**
     * 并行发送竞价请求，<b>非阻塞</b>。所有 bidder 完成 / 超时 / cancel 后，会通过
     * {@link #continueAfterBids} 回调推进 {@link #doExecute} 的 Phase 3。
     *
     * <p>并发模型：
     * <ul>
     *   <li>{@code remainingBids} 初始 = bidder 数；每个 bidder 完成时 CAS 递减</li>
     *   <li>CAS 归零的那个线程负责 {@code executor.execute(continueAfterBids)} 推进</li>
     *   <li>超时调度：{@link #forceFinishBids} 强制 set 0（也意味着"完成"），并直接触发推进</li>
     *   <li>cancel：{@link #forceFinishBids} 同样强制归零</li>
     *   <li>本方法立即返回，外层 doExecute 继续运行到方法结束（不是阻塞）</li>
     * </ul>
     * </p>
     */
    private void performParallelBidding(Context context, AdRequestParams params, AdUnitConfig unitConfig, List<AdSourceConfig> biddingSources, AdStrategyCallback callback, ScheduledFuture<?> timeoutFuture) {

        final List<UnionAdResponse> bidResponses = new CopyOnWriteArrayList<>();
        // 初始化为 biddingSources.size()；所有 bidder 共享同一个计数器
        remainingBids.set(biddingSources.size());

        // 构造 BiddingContext，让 performSingleBid / doBid / continueAfterBids 共享
        BiddingContext ctx = new BiddingContext(context, params, unitConfig,
                biddingSources, bidResponses, callback, timeoutFuture);
        // 暴露给超时调度 lambda：到点时如果 ctx 已建好，能拿到已有 bidResponses（Case 1 修复关键）
        ctxRef.set(ctx);

        for (AdSourceConfig sourceConfig : biddingSources) {
            try {
                executor.execute(() -> performSingleBid(ctx, sourceConfig));
            } catch (RejectedExecutionException e) {
                // H1 修复：BIDDING_POOL 拒绝任务时（如线程池满），count 掉本 bidder 推进流程，
                // 避免 remainingBids 永不归零导致业务方收不到回调
                FsLogger.e("BiddingStrategy", "Bidding executor rejected task for "
                        + sourceConfig.getSdkName() + ", skip this bidder", e);
                tryContinue(ctx);
            }
        }
        // 注意：这里不阻塞。bidder 完成 / 超时 / cancel 会通过 continueAfterBids 推进。
    }

    /**
     * 强制将 remainingBids 归零（不投递推进）。用于 cancel 路径。
     * <p>cancel 之后，bidder 的 in-flight 回调仍会触发，它们会调 {@link #tryContinue}；
     * 此时 {@code prev <= 0}（已被置 0）走 return 分支，<b>不</b>重复推进。
     * cancel 自身不需要主动推进，因为取消语义下不再执行 Phase 3。</p>
     */
    private void forceFinishBids() {
        remainingBids.set(0);
    }

    /**
     * 尝试递减 remainingBids；归零时投递 continueAfterBids 推进 doExecute。
     * <p>每次"自认为完成"的路径（onSuccess / onFailure / init-failure / exception）都需要调一次。
     * 内部 CAS 避免重复推进。</p>
     */
    private void tryContinue(BiddingContext ctx) {
        // CAS 递减；如果本调用是最后一个完成者，归零后负责推进
        int prev = remainingBids.getAndDecrement();
        if (prev <= 0) {
            // 已归零（被 forceFinishBids / 超时调度 抢先），本调用者不负责推进
            return;
        }
        if (prev == 1) {
            // 自己就是最后一个完成者 → 推进 doExecute Phase 3
            executor.execute(() -> continueAfterBids(ctx));
        }
        // prev > 1：还有别的未完成，不推进
    }

    /**
     * 全部 bidder 完成 / 超时 后的统一推进入口。
     * 
     * 
     * <p>逻辑：判定 cancel/timeout/adLoaded → 抢 Phase 3 启动权（CAS）→ 提取 bids → 排序 → 进入 Phase 3 加载。</p>
     */
    private void continueAfterBids(BiddingContext ctx) {

        // 已被 cancel, 不再继续。
        // 注意：不检查 timeoutFlipped —— 超时任务自身会投递 continueAfterBids 走 Phase 3，
        // 由 phase3Started CAS 保证唯一性，若在此处拦截会导致超时后业务方收不到任何回调（C1 修复）。
        if (cancelFlipped.get()) {
            FsLogger.d("BiddingStrategy", "Bidding cancelled during parallel bidding, stop");
            return;
        }

        // 已加载成功（理论上不该走到这里 —— adLoadedFlipped 应在 continueAfterBids 返回后由 Phase 3 置位；
        // 此处保留作为双保险，避免极端时序下重复回调）
        if (adLoadedFlipped.get()) {
            FsLogger.d("BiddingStrategy", "Bidding ad already loaded, skip continue path");
            return;
        }

        // 抢 Phase 3 启动权：无论触发源是"最后一个 bidder 正常完成"还是"超时调度抢到令牌"，
        // 都通过这个 CAS 抢一次；抢到者才有权走 Phase 3。抢不到说明 Phase 3 已经被别人启动了
        // （典型场景：超时调度到点时，正常的 tryContinue 已经把最后一个完成者投递进 executor，
        //  两条 continueAfterBids 在 executor 队列里都排队 —— 第二条到达时 CAS 抢不到则退出，
        //  避免双 Phase 3 浪费 + 避免对同一份 bidResponses 重复加载）
        if (!ctx.phase3Started.compareAndSet(false, true)) {
            FsLogger.d("BiddingStrategy", "Phase 3 already started by another continueAfterBids, skip duplicate");
            return;
        }
        UnionAdResponse cachedBid = null;
        cachedBid=BidFailCacheManager.getInstance().claimBestValidBid(ctx.params.getSlotId(), System.currentTimeMillis());
        if (cachedBid != null) {
            ctx.bidResponses.add(cachedBid);
            FsLogger.d("BiddingStrategy", "Bidding: added cached fail-bid " + cachedBid.getSdkName() + " ecpm=" + cachedBid.getEcpm() + " (fromCache=" + cachedBid.isFromCache() + ")");
        }

        List<UnionAdResponse> validBids = extractAndSortBids(ctx.bidResponses);
        if (validBids.isEmpty()) {
            ctx.timeoutFuture.cancel(false);
            notifyNoFill(ctx.callback, "No valid bids received for " + ctx.params.getSlotId());
            return;
        }

        logSortedBids(validBids);

        // ===== Phase 3: 取最高价加载 =====
        // 次高价 addFailBid 已在 loadFromSortedBids 入口处理（抽象进去，保持职责内聚）；
        // 加载成功 → notifyAdLoaded；加载失败（bid.getNativeAd() == null）→ notifyNoFill
        loadFromSortedBids(ctx.context, ctx.params, validBids, ctx.biddingSources, ctx.callback, ctx.timeoutFuture);
    }

    /**
     * 单个广告源的竞价任务入口。分两段执行：
     * <ol>
     *   <li>{@code ensurePlatformInitialized}：异步确保平台 SDK 初始化，
     *       {@code onInitSuccess} 触发后投递到 {@code executor} 执行 {@code doBid}，
     *       {@code onInitFailure} 触发后调用 {@link #tryContinue} 推进</li>
     *   <li>{@code doBid}：直接调用 {@code adapter.request}，回调在任意线程触发，
     *       回调内只更新 {@code bidResponses} + 调用 {@link #tryContinue} 推进。
     *       微观超时由各 Adapter 内部 AdLoadTimeout 兜底</li>
     * </ol>
     */
    private void performSingleBid(BiddingContext ctx, AdSourceConfig sourceConfig) {
        if (cancelFlipped.get()) {
            // 已 cancel，count 掉本 bidder 后由"最后一个完成者"推进推进逻辑
            tryContinue(ctx);
            return;
        }

        AdAdapter adapter = StrategyUtils.resolveAdapter(sourceConfig);
        if (adapter == null || !adapter.supportBidding()) {
            FsLogger.w("BiddingStrategy", "Source " + sourceConfig.getSdkName() + " does not support bidding, skip");
            tryContinue(ctx);
            return;
        }

        AdAdapterRegistry.getInstance().ensureInitializedAsync(getAppContext(ctx.context), adapter, sourceConfig.getAppId(), sourceConfig.getToken(), new AdInitListener() {
            @Override
            public void onInitSuccess(AdAdapter a) {
                // 初始化成功 → 投递到 executor 执行 request（保持每 source 独立线程语义）
                try {
                    executor.execute(() -> doBid(ctx, sourceConfig, adapter));
                } catch (RejectedExecutionException e) {
                    // H1 修复：executor 拒绝任务时 count 掉本 bidder，避免 remainingBids 永不归零
                    FsLogger.e("BiddingStrategy", "Bidding executor rejected doBid task for "
                            + sourceConfig.getSdkName(), e);
                    tryContinue(ctx);
                }
            }

            @Override
            public void onInitFailure(AdAdapter a, int code, String msg) {
                // 初始化失败 → 跳过该 bidder，count 掉本 bidder
                FsLogger.w("BiddingStrategy", "Bidder " + sourceConfig.getSdkName() + " init failed: [" + code + "] " + msg + ", skip bid");
                tryContinue(ctx);
            }
        });
    }

    /**
     * 当前源已成功初始化，发起竞价请求。
     * <p>回调在任意线程触发后通过 {@link #tryContinue} 推进外层流程。</p>
     */
    private void doBid(BiddingContext ctx, AdSourceConfig sourceConfig, AdAdapter adapter) {
        try {
            if (cancelFlipped.get()) {
                tryContinue(ctx);
                return;
            }

            FsLogger.d("BiddingStrategy", "Bid requesting: sdkName=" + sourceConfig.getSdkName() + " sdkType=" + sourceConfig.getSdkType().getSdkName() + " adUnitId=" + sourceConfig.getAdUnitId() + " bidFloor=" + sourceConfig.getBidFloor());

            adapter.request(getAppContext(ctx.context), ctx.params, sourceConfig, new AdCallback() {
            @Override
            public void onLoaded(UnionAdResponse response) {
                try {
                    handleBidSuccess(response, sourceConfig, ctx);
                } finally {
                    tryContinue(ctx);
                }
            }

                @Override
                public void onCachedSuccess(UnionAdResponse response) {
                    // 竞价阶段不关心素材缓存状态，仅记录
                    FsLogger.d("BiddingStrategy", "Bid cached: " + sourceConfig.getSdkName());
                }

                @Override
                public void onLoadFailed(int errorCode, String errorMsg) {
                    try {
                        FsLogger.w("BiddingStrategy", "Bid failed: " + sourceConfig.getSdkName() + " [" + errorCode + "] " + errorMsg);
                    } finally {
                        tryContinue(ctx);
                    }
                }
            });

        } catch (Exception e) {
            FsLogger.e("BiddingStrategy", "Bid error: " + sourceConfig.getSdkName(), e);
            // 同步异常也要 count 掉本 bidder，否则 doExecute 永远不推进
            tryContinue(ctx);
        }
    }

    /**
     * 处理竞价成功回调：过滤低于底价的响应、过滤未就绪的广告，有效结果加入 bidResponses。
     * 
     */
    private void handleBidSuccess(UnionAdResponse response, AdSourceConfig sourceConfig, BiddingContext ctx) {
        if (timeoutFlipped.get()) {
            // 已超时，放进竞败缓存（本次不再参与比价）
            BidFailCacheManager.getInstance().addFailBid(ctx.params.getSlotId(), response);
            AdAdapter adapter = response.getAdapter();
            if (adapter != null) {
                adapter.reportBidFail(response.getNativeAd(), BidLossReason.BIDDING_TIMEOUT);
            }
            FsLogger.d("BiddingStrategy", "Bid success but already timeout, add to fail cache: " + sourceConfig.getSdkName() + " ecpm=" + response.getEcpm());
            return;
        }
        // 广告未就绪（素材失效/过期），不参与竞价
        if (!response.isReady()) {
            FsLogger.w("BiddingStrategy", "Bid filtered by isReady=false: " + sourceConfig.getSdkName() + " ecpm=" + response.getEcpm());
            AdAdapter adapter = response.getAdapter();
            if (adapter != null) {
                adapter.reportBidFail(response.getNativeAd(), BidLossReason.LOST_NOT_READY);
            }
            // 未就绪广告进竞败缓存也没意义，直接丢弃
            return;
        }
        if (response.getEcpm() >= sourceConfig.getBidFloor()) {
            ctx.bidResponses.add(response);
            FsLogger.d("BiddingStrategy", "Bid success: " + sourceConfig.getSdkName() + " ecpm=" + response.getEcpm());
        } else {
            FsLogger.d("BiddingStrategy", "Bid filtered by floor: " + sourceConfig.getSdkName() + " ecpm=" + response.getEcpm() + " < floor=" + sourceConfig.getBidFloor());
            AdAdapter adapter = response.getAdapter();
            if (adapter != null) {
                adapter.reportBidFail(response.getNativeAd(), BidLossReason.LOST_BELOW_FLOOR);
            }
        }
    }

    /**
     * 从线程安全的 bidResponses 中复制数据，并按价格降序排序。
     *
     * <p>CopyOnWriteArrayList 本身迭代安全，无需额外同步。</p>
     */
    private List<UnionAdResponse> extractAndSortBids(List<UnionAdResponse> bidResponses) {
        List<UnionAdResponse> copy = new ArrayList<>(bidResponses);
        Collections.sort(copy, (a, b) -> Double.compare(b.getEcpm(), a.getEcpm()));
        return copy;
    }

    /**
     * 打印排序后的竞价结果，便于调试。
     */
    private void logSortedBids(List<UnionAdResponse> sortedBids) {
        FsLogger.d("BiddingStrategy", "Bidding collected " + sortedBids.size() + " valid bids:");
        for (int i = 0; i < sortedBids.size(); i++) {
            UnionAdResponse r = sortedBids.get(i);
            FsLogger.d("BiddingStrategy", "  #" + (i + 1) + ": " + r.getSdkName() + " ecpm=" + r.getEcpm());
        }
    }

    /**
     * 只取最高价广告加载。
     * 
     * 
     * <p>失败/不带 nativeAd → notifyNoFill（次高价已在入口处批量 addFailBid 供下次 bidding 复用）。</p>
     */
    private void loadFromSortedBids(Context context, AdRequestParams params, List<UnionAdResponse> sortedBids, List<AdSourceConfig> biddingSources, AdStrategyCallback callback, ScheduledFuture<?> timeoutFuture) {
        if (cancelFlipped.get()) {
            notifyNoFill(callback, "Bidding cancelled");
            return;
        }

        // 注意：不再检查 timeoutFlipped —— phase3Started CAS 已保证 loadFromSortedBids 全链路只调用一次，
        // 超时任务与正常完成路径二者只有一方能抢到 CAS 进入此处。若在此处拦截会导致超时后无回调（C1 修复）。

        if (sortedBids.isEmpty()) {
            // 全部竞价者都尝试完毕，无填充
            timeoutFuture.cancel(false);
            notifyNoFill(callback, "No valid bid to load for " + params.getSlotId());
            return;
        }

        // C3 修复：遍历降级 —— 按 eCPM 降序找第一个有效 bid（nativeAd != null && isReady），
        // 不再只试最高价。避免最高价 nativeAd=null（如竞败缓存项）时直接 noFill 浪费其余有效 bid。
        UnionAdResponse chosen = null;
        for (UnionAdResponse bid : sortedBids) {
            if (bid.getNativeAd() == null) {
                FsLogger.d("BiddingStrategy", "Skip bid (nativeAd=null): " + bid.getSdkName()
                        + " ecpm=" + bid.getEcpm() + " fromCache=" + bid.isFromCache());
                continue;
            }
            if (!bid.isReady()) {
                FsLogger.d("BiddingStrategy", "Skip bid (isReady=false): " + bid.getSdkName()
                        + " ecpm=" + bid.getEcpm());
                continue;
            }
            chosen = bid;
            break;
        }

        if (chosen == null) {
            FsLogger.w("BiddingStrategy", "No valid bid with nativeAd && isReady, noFill");
            timeoutFuture.cancel(false);
            notifyNoFill(callback, "No valid bid with nativeAd for " + params.getSlotId());
            // 仍把有效 bid 回写缓存供下次 bidding 复用（returnToCache 内部会过滤中毒项）
            returnToCache(params, sortedBids, null);
            return;
        }

        FsLogger.d("BiddingStrategy", "Chosen bidder: " + chosen.getSdkName()
                + " ecpm=" + chosen.getEcpm() + " fromCache=" + chosen.isFromCache());

        adLoadedFlipped.set(true);
        timeoutFuture.cancel(false);
        notifyAdLoaded(callback, chosen);
        returnToCache(params, sortedBids, chosen);
    }

    /**
     * 把未选中的有效 bid 回写竞败缓存供下次 bidding 复用。
     *
     * <p>C2 修复：跳过以下几类项，避免缓存中毒：
     * <ul>
     *   <li>{@code chosen} —— 本次已回调给业务方的 bid</li>
     *   <li>{@code isFromCache()==true} —— 来自竞败缓存的项，nativeAd 已被置 null，回写会永久中毒</li>
     *   <li>{@code nativeAd==null} —— 无平台 SDK 广告对象，回写后下次取出仍无法加载</li>
     *   <li>{@code isReady()==false} —— 未就绪广告进缓存无意义</li>
     * </ul>
     * </p>
     *
     * @param chosen 本次已选中的 bid（可为 null，表示全部未选中）
     */
    private static void returnToCache(AdRequestParams params, List<UnionAdResponse> sortedBids,
                                      UnionAdResponse chosen) {
        for (UnionAdResponse bid : sortedBids) {
            if (bid == chosen) {
                continue;
            }
            // C2 修复：缓存项不回写（nativeAd 已被置 null，回写会永久中毒）
            if (bid.isFromCache()) {
                FsLogger.d("BiddingStrategy", "Skip returnToCache (fromCache): " + bid.getSdkName());
                continue;
            }
            // C2 修复：nativeAd=null 的项不回写
            if (bid.getNativeAd() == null) {
                FsLogger.d("BiddingStrategy", "Skip returnToCache (nativeAd=null): " + bid.getSdkName());
                continue;
            }
            if (!bid.isReady()) {
                FsLogger.d("BiddingStrategy", "Skip returnToCache: isReady=false for " + bid.getSdkName());
                continue;
            }
            AdAdapter adapter = bid.getAdapter();
            if (adapter != null) {
                adapter.reportBidFail(bid.getNativeAd(), BidLossReason.LOST_TO_HIGHER_BID);
            }
            BidFailCacheManager.getInstance().addFailBid(params.getSlotId(), bid);
        }
    }

    /**
     * 从源列表中查找对应 adUnitId 的配置。
     *
     * <p>adUnitId 是竞价链路中的唯一确定性标识，
     * 比 {@code sdkName} 更精准（一家 SDK 可配置多个 adUnitId 参与竞价）。</p>
     */
    private AdSourceConfig findSourceConfig(List<AdSourceConfig> sources, String adUnitId) {
        if (adUnitId == null) {
            return null;
        }
        for (AdSourceConfig src : sources) {
            if (adUnitId.equals(src.getAdUnitId())) {
                return src;
            }
        }
        return null;
    }

    /**
     * 获取 Application Context，避免持有 Activity 引用导致内存泄漏。
     */
    private Context getAppContext(Context context) {
        return context.getApplicationContext();
    }

    /**
     * 取消时强制归零 remainingBids，让"最后一个真正完成的 bidder"或下次回调触发推进。
     * <p>不再持有 CountDownLatch，改为事件驱动推进；onCancel
     * 只需强制归零，无需触发推进（推进权交给下一个完成的 bidder / 业务方 cancel 后的
     * 自然结束路径）。</p>
     * <p>对已收集的报价上报 {@link BidLossReason#CHAIN_CANCELLED}。</p>
     */
    @Override
    protected void onCancel() {
        super.onCancel();
        BiddingContext ctx = ctxRef.get();
        if (ctx != null) {
            for (UnionAdResponse bid : ctx.bidResponses) {
                AdAdapter adapter = bid.getAdapter();
                if (adapter != null) {
                    adapter.reportBidFail(bid.getNativeAd(), BidLossReason.CHAIN_CANCELLED);
                }
            }
        }
        forceFinishBids();
    }
}
