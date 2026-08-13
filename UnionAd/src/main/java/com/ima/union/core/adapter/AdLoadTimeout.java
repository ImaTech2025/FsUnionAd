package com.ima.union.core.adapter;



import com.ima.union.utils.FsLogger;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.concurrent.ExecutorManager;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 适配器层广告请求超时控制器。
 *
 * <p>很多广告 SDK 的请求接口（如穿山甲的 loadFullScreenVideoAd、loadRewardVideoAd、loadFeedAd，
 * 飞梭的 loadXxx）签名中没有 timeout 参数，SDK 内部有自己的超时机制，但这跟策略方要求
 * "每个 source 的 timeout 字段必须真正生效"的诉求不一定一致。</p>
 *
 * <p>本类用 {@link ScheduledExecutorService#schedule} 在适配器层做兜底：超时后回调
 * {@link AdCallback#onLoadFailed(int, String)}，
 * 错误码为 {@link FsAdErrorCode#SDK_LOAD_TIMEOUT}（2002）。</p>
 *
 * <p>一旦 SDK 实际回调（成功或失败）先触发，调用方应调用 {@link #finish()} 把状态置为已完成；
 * 后续 SDK 兜底回调（如果有）会被 {@link #run()} 内的 {@code completed} 检查过滤掉。
 * 反之，如果 timeout 触发了，那么后续 SDK 的实际回调不应该再调 callback（避免双回调）。
 * Adapter 实现中应在 SDK 回调里先判 {@code timeoutCtrl.isCompleted()} 再决定是否转发。</p>
 *
 * <p>线程池由 {@link ExecutorManager} 统一管理（{@link ExecutorManager.PoolType#TIMEOUT_SCHEDULE}），
 * 不再各自 new 调度线程池。</p>
 */
public final class AdLoadTimeout implements Runnable {
    private static final String TAG = "AdLoadTimeout";
    /** 适配器层 timeout 错误码 */
    public static final int ERR_ADAPTER_TIMEOUT = FsAdErrorCode.SDK_LOAD_TIMEOUT;

    private final long timeoutMs;
    private final String sdkName;
    private final AdCallback callback;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> future;

    public AdLoadTimeout(long timeoutMs, String sdkName, AdCallback callback) {
        this.timeoutMs = timeoutMs;
        this.sdkName = sdkName;
        this.callback = callback;
        this.scheduler = ExecutorManager.getScheduled(ExecutorManager.PoolType.TIMEOUT_SCHEDULE);
    }

    /** 启动 timeout 计时，到期后自动触发失败回调 */
    public void start() {
        future = scheduler.schedule(this, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** SDK 已回调（成功或失败），取消 pending timeout，避免双回调 */
    public void finish() {
        if (completed.compareAndSet(false, true)) {
            ScheduledFuture<?> f = future;
            if (f != null) {
                f.cancel(false);
            }
        }
    }

    /** 检查是否已 completed（SDK 回调里用来过滤） */
    public boolean isCompleted() {
        return completed.get();
    }

    @Override
    public void run() {
        if (!completed.compareAndSet(false, true)) return;
        FsLogger.w(TAG, "Ad request timeout after " + timeoutMs + "ms, sdkName=" + sdkName);
        if (callback != null) {
            callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_TIMEOUT,
                    FsAdErrorCode.buildMsg("SDK请求超时(" + timeoutMs + "ms)", sdkName, 0, ""));
        }
    }
}
