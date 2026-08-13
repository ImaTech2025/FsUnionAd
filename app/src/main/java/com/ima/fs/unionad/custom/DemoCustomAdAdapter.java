package com.ima.fs.unionad.custom;

import android.content.Context;
import android.util.Log;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdInitCallback;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo 自定义广告适配器 — 演示 App 层如何扩展聚合 SDK。
 *
 * <p>这是一个模拟适配器，不依赖真实的三方 SDK，用于验证：
 * <ul>
 *   <li>自定义 Adapter 注册后能被聚合 SDK 自动检测</li>
 *   <li>Bidding / Waterfall / Hybrid 策略下多源并发/串行执行</li>
 *   <li>日志中可观察到自定义 Adapter 的完整生命周期</li>
 *   <li>如何读取 {@link AdRequestParams} 中的尺寸字段（{@code expressViewSize} / {@code imageSize}）</li>
 * </ul>
 *
 * <p>接入方参考此示例，替换为真实的广告平台 SDK 即可。</p>
 *
 * <p>API 演进说明：{@code bid()} 和 {@code loadAd()} 已合并为统一的 {@code request()}，
 * 回调从 {@code AdBidCallback} / {@code AdLoadCallback} 统一为 {@link AdCallback}，
 * 响应模型从 {@code AdObject} / {@code BidResponse} 统一为 {@link UnionAdResponse}。</p>
 *
 * <p>{@code request()} 签名携带 {@link AdRequestParams}，
 * 业务方可传入模板尺寸 / 自渲染图片尺寸；本 Demo 仅记录日志演示，真实实现可按需透传给底层 SDK。</p>
 */
public class DemoCustomAdAdapter implements AdAdapter {

    private static final String TAG = "DemoCustomAdapter";
    public static final String VERSION = "1.0.0";
    public static final String SOURCE_ID = "demo_custom";

    /**
     * 自定义平台类型标识，通过 {@link AdSdkType#of(String, String)} 注册。
     * <p>接入方参考此模式：每个自定义广告源都应拥有独立的 AdSdkType 实例，
     * 便于日志/打点/路由区分，而不是全部挤在同一个 CUSTOM 桶里。</p>
     */
    public static final AdSdkType AD_SDK_TYPE = AdSdkType.of("DEMO", "Demo广告平台");

    /**
     * 初始化 appId（用于 isInitialized() 委托到 AdAdapterRegistry.isInited()）。
     * <p>接入方参考此模式：存储 appId，isInitialized() 统一委托给 Registry，无需自行维护 boolean。</p>
     */
    private volatile String initAppId;

    @Override
    public AdSdkType getSdkType() {
        return AD_SDK_TYPE;
    }

    @Override
    public String getAdapterVersion() {
        return VERSION;
    }

    @Override
    public String getSdkName() {
        return SOURCE_ID;  // "demo_custom"，需与策略 JSON 中 sdkName 一致
    }

    /**
     * 统一委托到 {@link AdAdapterRegistry#isInited(AdSdkType, String)} 查询，
     * 与 4 个内置平台适配器保持一致。
     */
    @Override
    public boolean isInitialized() {
        return initAppId != null && AdAdapterRegistry.getInstance().isInited(getSdkType(), initAppId);
    }

    @Override
    public void initialize(Context context, String appId, String token, AdInitCallback callback) {
        Log.i(TAG, "initialize() appId=" + appId + " token=" + token);
        // 记录 appId，供 isInitialized() 委托到 Registry 查询
        this.initAppId = appId;
        // 模拟异步初始化
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Log.i(TAG, "initialize() success");
            if (callback != null) callback.onInitSuccess();
        }, 200);
    }

    @Override
    public boolean supportBidding() {
        return true;
    }

    @Override
    public void request(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        Log.i(TAG, "request() sourceId=" + sourceConfig.getSdkName()
                + " format=" + sourceConfig.getAdFormat()
                + " supportBidding=" + supportBidding());

        // 演示：读取 AdRequestParams 中的尺寸字段（业务方可按需使用）
        final int expressW, expressH, imgW, imgH;
        if (params != null) {
            int[] expressSize = params.getExpressViewAcceptedSize();
            if (expressSize != null) {
                expressW = expressSize[0];
                expressH = expressSize[1];
                Log.i(TAG, "request() expressViewSize=" + expressW + "x" + expressH + " dp");
            } else {
                expressW = 0;
                expressH = 0;
            }
            int[] imageSize = params.getImageAcceptedSize();
            if (imageSize != null) {
                imgW = imageSize[0];
                imgH = imageSize[1];
                Log.i(TAG, "request() imageSize=" + imgW + "x" + imgH + " px");
            } else {
                imgW = 0;
                imgH = 0;
            }
        } else {
            expressW = 0; expressH = 0; imgW = 0; imgH = 0;
        }

        // 模拟竞价：根据 sdkName 的 hash 生成一个确定性 eCPM(单位:分,500~1500),便于复现
        double ecpm = 500.0 + (Math.abs(sourceConfig.getSdkName().hashCode()) % 1000) / 10.0;

        // 模拟异步召回（> adSourceConfig.timeout 也能正常通过 AdCallback 回调）
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Map<String, Object> extras = new HashMap<>();
            extras.put("demo_payload", "custom_ad_payload_" + System.currentTimeMillis());
            if (expressW > 0 && expressH > 0) {
                extras.put("express_w", expressW);
                extras.put("express_h", expressH);
            }
            if (imgW > 0 && imgH > 0) {
                extras.put("img_w", imgW);
                extras.put("img_h", imgH);
            }

            UnionAdResponse response = new UnionAdResponse.Builder()
                    .sdkName(sourceConfig.getSdkName())
                    .adFormat(sourceConfig.getAdFormat())
                    .sdkType(AD_SDK_TYPE)
                    .adUnitId(sourceConfig.getAdUnitId())
                    .ecpm(ecpm)
                    .expireTimeMs(System.currentTimeMillis() + sourceConfig.getExpireTimeMs())
                    .extra(extras)
                    .build();

            Log.i(TAG, "request() success ecpm=" + ecpm);
            if (callback != null) {
                callback.onLoaded(response);
                callback.onCachedSuccess(response);
            }
        }, 300);
    }

    @Override
    public void destroy() {
        Log.i(TAG, "destroy()");
        this.initAppId = null;
    }
}
