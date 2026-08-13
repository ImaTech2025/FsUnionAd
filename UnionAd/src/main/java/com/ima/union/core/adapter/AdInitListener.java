package com.ima.union.core.adapter;

/**
 * 适配器初始化结果回调，供策略层在收到初始化结果后决定下一步操作。
 * <p>由 {@link AdAdapterRegistry#ensureInitializedAsync} 在适配器初始化完成（或失败、超时）时回调。
 * 回调线程：与平台 SDK 的 {@code AdInitCallback} 触发线程一致（通常是 SDK 内部 init 线程）。
 * 如需在主线程处理，请通过 {@link com.ima.union.core.concurrent.ExecutorManager#postToMain(Runnable)} 切换。</p>
 */
public interface AdInitListener {
    /**
     * 平台 SDK 初始化成功。
     *
     * @param adapter 已成功初始化的适配器
     */
    void onInitSuccess(AdAdapter adapter);

    /**
     * 平台 SDK 初始化失败或等待超时。
     *
     * @param adapter   初始化失败的适配器
     * @param errorCode 错误码
     *                  <ul>
     *                    <li>0 — 等待初始化回调超时（{@link AdAdapterRegistry#INIT_TIMEOUT_MS}）</li>
     *                    <li>-1 — 等待初始化过程中被中断</li>
     *                    <li>其它非 0 值 — 平台 SDK 自身返回的 errorCode</li>
     *                  </ul>
     * @param errorMsg  错误描述
     */
    void onInitFailure(AdAdapter adapter, int errorCode, String errorMsg);
}
