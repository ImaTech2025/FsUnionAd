package com.ima.union.core.strategy;

import android.content.Context;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdapterFactory;
import com.ima.union.core.model.AdLoadResult;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.utils.FsLogger;

public class StrategyUtils {


    /**
     * 根据 AdSourceConfig 解析对应的 AdAdapter（统一入口，覆盖内置平台和自定义平台）。
     *
     * <p>查找顺序：
     * <ol>
     *   <li>Registry 查找：{@code getAdapter(sdkType, format)}</li>
     *   <li>反射懒注册：若 registry miss 且 sourceConfig 配置了 {@code adapterClassName}，
     *       通过 {@link AdapterFactory#createIfAbsent(AdSourceConfig)} 反射创建并注册</li>
     * </ol>
     *
     * <p>Registry key 统一后，内置平台和自定义平台均通过
     * {@code getAdapter(sdkType, format)} 一个方法查找，不再分支 CUSTOM。</p>
     */
    public static AdAdapter resolveAdapter(AdSourceConfig sourceConfig) {
        AdSdkType sdkType = sourceConfig.getSdkType();
        if (sdkType == null) return null;

        // 1. 注册表查找
        AdAdapter adapter = AdAdapterRegistry.getInstance().getAdapter(sdkType, sourceConfig.getAdFormat());
        if (adapter != null) {
            return adapter;
        }

        // 2. 反射懒注册（仅自定义 ADN 配置了 adapterClassName 时生效）
        adapter = AdapterFactory.createIfAbsent(sourceConfig);
        if (adapter != null) {
            return adapter;
        }

        // 3. 彻底找不到
        FsLogger.w("StrategyUtils", "No adapter registered for sdkType=" + sdkType.getKey()
                + " format=" + sourceConfig.getAdFormat()
                + ". Make sure the adapter is registered via FsUnionSDK.registerCustomAdapter()"
                + " or configured with adapterClassName in the backend JSON.");
        return null;
    }

    /**
     * 异步发起广告请求，结果通过 {@code callback} 回调通知。
     *
     *
     *
     * <p>超时职责分配（自底向上）：</p>
     * <ul>
     *   <li><b>微观超时</b>（{@code AdSourceConfig.timeout}）：由各 Adapter 内部用
     *       {@link com.ima.union.core.adapter.AdLoadTimeout} 兜底，或透传给平台 SDK
     *       （如 Baidu SplashAd.KEY_TIMEOUT）。超时后会以
     *       {@code callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_TIMEOUT, ...)}
     *       形式直接回调到本方法的 {@code RequestCallback.onResult(Failure)}。</li>
     *   <li><b>宏观超时</b>（{@code AdUnitConfig.timeoutMs}）：由策略层
     *       {@code WaterfallStrategy} / {@code HybridStrategy} / {@code BiddingStrategy}
     *       各自用 {@code ScheduledExecutorService} 控制整段链路，到点触发
     *       {@code onNoFill}。{@code WaterfallStrategy.onLoadResult} 头部
     *       也会判 {@code timeoutFlipped} 把"在途响应"丢弃，避免宏观超时后
     *       又被单源 success 二次触发 onAdLoaded。</li>
     * </ul>
     *
     * <p>本方法只做三件事：</p>
     * <ol>
     *   <li>从 {@code sourceConfig} 兜底构造 {@code AdRequestParams}（若 {@code params} 为 null）；</li>
     *   <li>包装 {@link AdCallback}，把 SDK 回调的 {@code onLoaded}/{@code onLoadFailed}
     *       转成 {@link AdLoadResult} 推给 {@code callback}；{@code onCachedSuccess} 仅记日志不做状态变更；</li>
     *   <li>调 {@code adapter.request}，同步异常直接转成 {@code Failure} 回调。</li>
     * </ol>
     *
     * <p>本方法本身<b>非阻塞</b>，立即返回。调用方应在 {@code callback} 内推进下一源
     * （即"回调驱动式"，与 {@link com.ima.union.core.strategy.BiddingStrategy} 中
     * {@code doBid} 的处理方式一致）。</p>
     *
     * @param context      上下文
     * @param adapter      广告适配器
     * @param params       请求级参数（透传尺寸等），可为 null（null 时会用 adUnitId 兜底构造空参数）
     * @param sourceConfig 广告源配置
     * @param callback     异步结果回调（不可为 null）
     * @param logTag       日志 tag
     */
    public static void requestWithTimeout(Context context,
                                          AdAdapter adapter,
                                          AdRequestParams params,
                                          AdSourceConfig sourceConfig,
                                          RequestCallback callback,
                                          String logTag) {
        final String sdkName = sourceConfig.getSdkName();
        FsLogger.d("StrategyUtils", "request: sdkName=" + sdkName
                + " sdk=" + sourceConfig.getSdkType().getSdkName()
                + " perSourceTimeout=" + sourceConfig.getTimeout() + "ms");

        // ── 1. 兜底构造 AdRequestParams ──
        final AdRequestParams finalParams = params != null ? params : new AdRequestParams.Builder()
                .slotId(sourceConfig.getAdUnitId() != null ? sourceConfig.getAdUnitId() : "")
                .build();

        // ── 2. 包装 AdCallback：把 SDK 回调转成 AdLoadResult 推给上层 ──
        AdCallback wrappedCallback = new AdCallback() {
            @Override
            public void onLoaded(UnionAdResponse response) {
                callback.onResult(new AdLoadResult.Success(response));
            }

            @Override
            public void onCachedSuccess(UnionAdResponse response) {
                FsLogger.d("StrategyUtils", "onCachedSuccess: sdkName=" + sdkName);
            }

            @Override
            public void onLoadFailed(int errorCode, String errorMsg) {
                callback.onResult(new AdLoadResult.Failure(sdkName, errorCode, errorMsg));
            }
        };

        // ── 3. 发起请求：同步异常立即转 Failure；异步回调由 wrappedCallback 转发 ──
        try {
            adapter.request(context, finalParams, sourceConfig, wrappedCallback);
        } catch (Exception e) {
            FsLogger.e("StrategyUtils", "adapter.request() threw exception for " + sdkName, e);
            callback.onResult(new AdLoadResult.Failure(sdkName, -1, e.getMessage()));
        }
    }

    /**
     * 异步请求结果回调。SDK 实际回调（成功/失败）或适配器内 AdLoadTimeout 触发的失败
     * 都会通过 {@link #onResult(AdLoadResult)} 通知调用方，调用方据此推进
     * 下一源或回上层。
     */
    public interface RequestCallback {
        void onResult(AdLoadResult result);
    }

    private StrategyUtils() {
    }
}
