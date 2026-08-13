package com.ima.union.core.concurrent;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 聚合广告 SDK 内部线程池统一管理器（全静态实现）。
 *
 * <p><b>设计目标：</b>消除散落在各业务类（AdAdapterRegistry / WaterfallStrategy /
 * HybridStrategy / BiddingStrategy / CloudStrategyManager）中的 {@code newCachedExecutor}
 * 工厂方法重复定义，统一线程名前缀、keepAliveTime、daemon 标志位等参数。</p>
 *
 * <p><b>使用方式：</b>
 * <pre>
 *     ExecutorManager.get(ExecutorManager.PoolType.STRATEGY_LOAD).execute(() -&gt; { ... });
 *     ExecutorManager.getScheduled(ExecutorManager.PoolType.TIMEOUT_SCHEDULE)
 *                      .schedule(task, 5, TimeUnit.SECONDS);
 * </pre>
 * </p>
 *
 * <p><b>全静态实现的理由：</b>
 * <ul>
 *   <li>线程池在 ClassLoader 阶段由 JVM 保证线程安全地完成初始化, 无需 DCL/volatile 同步</li>
 *   <li>无单例 DCL 的"半构造对象"或"指令重排"风险</li>
 *   <li>静态 final 字段在多线程下的访问零开销, 不需要先 {@code getInstance()} 再调用方法</li>
 *   <li>避免某些 ClassLoader 场景下"两个 ClassLoader 加载出两个单例"问题</li>
 * </ul>
 * </p>
 *
 * <p><b>线程池模型：</b>所有池采用 cached 模型
 * （{@code corePoolSize=0, maxPoolSize=Integer.MAX_VALUE, keepAliveTime=15s,
 * SynchronousQueue}）：
 * <ul>
 *   <li>无任务时无线程，闲置 15s 后自动回收</li>
 *   <li>多业务并发立即拿到独立线程，不排队</li>
 *   <li>业务方调用入口已限流（≤10 个广告 slot），无线程爆炸风险</li>
 * </ul>
 * </p>
 *
 * <p>Scheduled 池通过 {@code ScheduledThreadPoolExecutor(0) +
 * allowCoreThreadTimeOut(true) + setKeepAliveTime(15s)} 实现等价 cached 模型。</p>
 */
public final class ExecutorManager {

    /**
     * 业务场景枚举。每个枚举值对应一个独立的线程池。
     * <p>命名以"业务动作"为粒度，便于排查堆栈时一眼看清调用源。</p>
     */
    public enum PoolType {
        /** 平台 SDK 初始化转发（AdAdapterRegistry） */
        SDK_INIT("AdapterInit"),
        /** 策略层串行推进任务（WaterfallStrategy onLoadResult / 状态推进） */
        STRATEGY_LOAD("StrategyLoad"),
        /** 整段广告请求链路的 timeout 调度（WaterfallStrategy / HybridStrategy） */
        TIMEOUT_SCHEDULE("TimeoutSchedule"),
        /** 云端广告策略配置拉取（CloudStrategyManager） */
        CONFIG_FETCH("ConfigFetch"),
        /** 并行竞价请求（BiddingStrategy） */
        BIDDING("Bidding");

        final String threadNamePrefix;
        PoolType(String prefix) { this.threadNamePrefix = prefix; }
    }

    private static final long KEEP_ALIVE_SECONDS = 15L;

    // ========== 静态池（ClassLoader 阶段初始化, JVM 保证线程安全） ==========

    private static final ExecutorService SDK_INIT_POOL = createCachedPool(PoolType.SDK_INIT.threadNamePrefix);
    private static final ExecutorService STRATEGY_LOAD_POOL = createCachedPool(PoolType.STRATEGY_LOAD.threadNamePrefix);
    private static final ScheduledExecutorService TIMEOUT_SCHEDULE_POOL = createScheduledPool(PoolType.TIMEOUT_SCHEDULE.threadNamePrefix);
    private static final ExecutorService CONFIG_FETCH_POOL = createCachedPool(PoolType.CONFIG_FETCH.threadNamePrefix);
    // H1 修复：BIDDING_POOL 从 fixed 池改为 cached 池。
    // 原 fixed + SynchronousQueue 在 bidder 数 > 线程数时抛 RejectedExecutionException，
    // 导致 remainingBids 永不归零、业务方收不到回调。bidder 数 ≤ 广告源数（通常 ≤10），
    // cached 池不会线程爆炸，且与 SDK_INIT_POOL 等保持一致。
    private static final ExecutorService BIDDING_POOL = createCachedPool(PoolType.BIDDING.threadNamePrefix);

    /** 主线程 Handler（业务回调统一投递到主线程时使用） */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private ExecutorManager() {
        // 工具类, 禁止实例化
        throw new AssertionError("No ExecutorManager instances for you!");
    }

    /**
     * 投递 Runnable 到主线程。
     * <p><b>线程优化:</b> 若调用方已经在主线程(Looper == MAIN_HANDLER 所在 Looper),
     * 直接 {@link Runnable#run()} 同步执行, 避免 {@link Handler#post} 的 MessageQueue 排队
     * 耗时(MessageQueue 插入 + 链表维护 ~ 几微秒, 在高频回调场景下不可忽略)。</p>
     * <p>调用方不再需要自行判断 Looper.myLooper(), 业务代码也更简洁:</p>
     * <pre>
     *     // 之前
     *     if (Looper.myLooper() == Looper.getMainLooper()) {
     *         runnable.run();
     *     } else {
     *         mainHandler.post(runnable);
     *     }
     *
     *     // 现在
     *     ExecutorManager.postToMain(runnable);
     * </pre>
     *
     * @param runnable 业务回调逻辑
     * @throws NullPointerException 若 {@code runnable} 为 null
     */
    public static void postToMain(Runnable runnable) {
        if (runnable == null) {
            throw new NullPointerException("runnable == null");
        }
        if (Looper.myLooper() == MAIN_HANDLER.getLooper()) {
            // 已在主线程, 同步执行避免 MessageQueue 排队
            runnable.run();
        } else {
            // 非主线程, 投递到主线程
            MAIN_HANDLER.post(runnable);
        }
    }

    /**
     * 获取指定业务场景的线程池。
     * <p>TIMEOUT_SCHEDULE 返回 {@link ScheduledExecutorService}（支持 schedule 调度），
     * 其余返回 {@link ExecutorService}（仅支持 execute 提交）。</p>
     */
    public static ExecutorService get(PoolType type) {
        switch (type) {
            case SDK_INIT:        return SDK_INIT_POOL;
            case STRATEGY_LOAD:   return STRATEGY_LOAD_POOL;
            case TIMEOUT_SCHEDULE: return TIMEOUT_SCHEDULE_POOL;
            case CONFIG_FETCH:    return CONFIG_FETCH_POOL;
            case BIDDING:         return BIDDING_POOL;
            default:
                throw new IllegalArgumentException("Unknown pool type: " + type);
        }
    }

    /**
     * 获取 TIMEOUT_SCHEDULE 的 ScheduledExecutorService（用于 {@code schedule} 调用）。
     */
    public static ScheduledExecutorService getScheduled(PoolType type) {
        if (type == PoolType.TIMEOUT_SCHEDULE) {
            return TIMEOUT_SCHEDULE_POOL;
        }
        throw new IllegalArgumentException("Pool " + type + " is not scheduled-type");
    }

    /**
     * 统一 graceful shutdown（业务进程退出 / SDK 卸载时调用）。
     * 给各池 5s 宽限期，超时强制 shutdownNow。
     */
    public static synchronized void shutdown() {
        shutdownQuietly(SDK_INIT_POOL);
        shutdownQuietly(STRATEGY_LOAD_POOL);
        shutdownQuietly(TIMEOUT_SCHEDULE_POOL);
        shutdownQuietly(CONFIG_FETCH_POOL);
        shutdownQuietly(BIDDING_POOL);
    }

    // ========== 池工厂（私有静态） ==========

    /**
     * 构造 cached 线程池：corePoolSize=0, maxPoolSize=MAX, keepAliveTime=15s, SynchronousQueue。
     * 多业务并发立即拿到独立线程，无任务时无线程，闲置 15s 后自动回收。
     */
    private static ExecutorService createCachedPool(String threadNamePrefix) {
        return new ThreadPoolExecutor(
                0, Integer.MAX_VALUE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                namedThreadFactory(threadNamePrefix));
    }

    /**
     * 构造 scheduled 线程池：corePoolSize=0, allowCoreThreadTimeOut=true, keepAliveTime=15s。
     * 等价 cached 模式的 scheduled 池；调度任务按需创建独立线程。
     */
    private static ScheduledExecutorService createScheduledPool(String threadNamePrefix) {
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(
                0, namedThreadFactory(threadNamePrefix));
        exec.setKeepAliveTime(KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
        exec.allowCoreThreadTimeOut(true);
        return exec;
    }

    /** 统一 ThreadFactory：daemon + 自增序号 + 业务前缀 */
    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicLong counter = new AtomicLong(0);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private static void shutdownQuietly(ExecutorService es) {
        try {
            es.shutdown();
            if (!es.awaitTermination(5, TimeUnit.SECONDS)) {
                es.shutdownNow();
            }
        } catch (InterruptedException e) {
            es.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
