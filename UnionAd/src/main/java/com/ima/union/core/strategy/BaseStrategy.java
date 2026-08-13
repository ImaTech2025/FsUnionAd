package com.ima.union.core.strategy;



import com.ima.union.utils.FsLogger;
import com.ima.union.core.concurrent.ExecutorManager;
import com.ima.union.core.model.UnionAdResponse;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 策略基类（WaterfallStrategy / BiddingStrategy / HybridStrategy 共用）。
 *
 * <p><b>设计目标</b>：抽取 3 个策略共用的状态字段、超时调度器、回调投递方法和 cancel 通用逻辑，
 * 消除 3 份重复的 AtomicBoolean + notifyAdLoaded/notifyNoFill 代码。</p>
 *
 * <p><b>公共状态</b>：
 * <ul>
 *   <li>{@link #cancelled} — 外部 cancel() 调用后置位</li>
 *   <li>{@link #cancelFlipped} — cancel() 内部置位,让在途回调/超时调度感知"已取消"</li>
 *   <li>{@link #timeoutFlipped} — 宏观/单段 timeoutMs 到点时置位,让在途回调感知"已超时"</li>
 *   <li>{@link #adLoadedFlipped} — 已有广告召回成功时置位,让超时调度不再回 noFill</li>
 * </ul>
 * </p>
 *
 * <p><b>公共方法</b>：
 * <ul>
 *   <li>{@link #notifyAdLoaded} / {@link #notifyNoFill} — 统一在主线程回调上层(内部已 Looper 短路)</li>
 *   <li>{@link #resetFlags} — execute() 入口处重置 4 个标志位(同一实例可能被复用)</li>
 *   <li>{@link #onCancel} — 模板方法,BaseStrategy 置 cancelled 后调用,子类可重写做额外清理</li>
 * </ul>
 * </p>
 *
 * <p><b>线程池</b>：{@link #scheduledExecutor} 统一从 {@link ExecutorManager} 拿
 * {@code TIMEOUT_SCHEDULE} 池, 子类不再各自 new 调度器。</p>
 */
abstract class BaseStrategy implements AdStrategy {

    /** 主线程回调用 TAG 前缀, 子类覆盖为具体策略名 */
    protected final String tag;

    /** 共享调度器(从 ExecutorManager 拿 cached 池) */
    protected final ScheduledExecutorService scheduledExecutor;

    // ========== 4 个状态标志位 (execute() 入口重置, 跨 execute() 互不影响) ==========

    /** 标记整体是否已被 cancel（外部 API 入口） */
    protected final AtomicBoolean cancelled = new AtomicBoolean(false);
    /** 标记整体是否已被 cancel（cancel 内部置位, 让在途回调/超时调度感知） */
    protected final AtomicBoolean cancelFlipped = new AtomicBoolean(false);
    /** 标记宏观/单段 timeoutMs 是否已到点（超时调度任务触发时置位） */
    protected final AtomicBoolean timeoutFlipped = new AtomicBoolean(false);
    /** 标记是否已有广告召回成功（一旦成功就不再回 noFill） */
    protected final AtomicBoolean adLoadedFlipped = new AtomicBoolean(false);

    protected BaseStrategy(String tag) {
        this.tag = tag;
        this.scheduledExecutor = ExecutorManager.getScheduled(ExecutorManager.PoolType.TIMEOUT_SCHEDULE);
    }

    /**
     * 每次 execute() 入口重置 4 个状态位。
     * <p>同一 strategy 实例可能被复用（虽然现在 AdStrategyManager 每次 new 新实例，
     * 但保持重置保证健壮性）。</p>
     */
    protected void resetFlags() {
        cancelled.set(false);
        cancelFlipped.set(false);
        timeoutFlipped.set(false);
        adLoadedFlipped.set(false);
    }

    /**
     * 统一在主线程回调广告加载成功。
     * <p>已用 {@link ExecutorManager#postToMain} 抽象, 内部 Looper 短路, 不再绕 MessageQueue 排队。</p>
     */
    protected void notifyAdLoaded(AdStrategyCallback callback, UnionAdResponse response) {
        ExecutorManager.postToMain(() -> callback.onAdLoaded(response));
    }

    /**
     * 统一在主线程回调无填充。
     */
    protected void notifyNoFill(AdStrategyCallback callback, String reason) {
        ExecutorManager.postToMain(() -> callback.onNoFill(reason));
    }

    /**
     * 通用 cancel 逻辑：
     * <ol>
     *   <li>置 cancelled + cancelFlipped + timeoutFlipped —— 在途的 tryNextSource/doLoad/超时回调 看到后直接 return</li>
     *   <li>调用 {@link #onCancel()} 模板方法, 子类做额外清理(如释放 CountDownLatch)</li>
     *   <li>子类也可在此基础上叠加自己的 cancel 路径</li>
     * </ol>
     */
    @Override
    public void cancel() {
        cancelled.set(true);
        cancelFlipped.set(true);
        timeoutFlipped.set(true);
        onCancel();
        FsLogger.d(tag, getClass().getSimpleName() + " cancelled");
    }

    /**
     * 模板方法：子类在 cancel 时需要做的额外清理工作。
     * <p>默认空实现。WaterfallStrategy / BiddingStrategy / HybridStrategy 可按需重写。</p>
     */
    protected void onCancel() {
        // 默认无操作, 子类按需重写
    }
}
