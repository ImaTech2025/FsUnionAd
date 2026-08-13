package com.ima.union.adapters.gdt;

import com.ima.union.utils.FsLogger;
import com.ima.union.utils.PrivacyUtils;
import com.ima.union.utils.SdkUtils;

import android.content.Context;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.adapter.AdInitCallback;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.qq.e.comm.managers.GDTAdSdk;

/**
 * 优量汇（GDT/腾讯广点通）适配器公共基类。
 *
 * <p>提取所有优量汇格式适配器共享的初始化、eCPM 提取、响应构建等逻辑。</p>
 *
 * <p>依赖：compileOnly com.qq.e.union:union:4.560.1470</p>
 */
public abstract class GdtBaseAdapter implements AdAdapter {

    protected static final String TAG = "GdtAdapter";

    /**
     * 初始化 appId（用于 isInitialized() 委托到 AdAdapterRegistry.isInited()）
     */
    protected volatile String initAppId;
    protected Context appContext;
    protected Boolean sdkAvailable = null;

    // ════════════════════════════════════════════════════════════════
    //  基础信息
    // ════════════════════════════════════════════════════════════════

    @Override
    public AdSdkType getSdkType() {
        return AdSdkType.GDT;
    }

    @Override
    public String getAdapterVersion() {
        return "1.0.0_gdt_4.560.1470";
    }

    @Override
    public boolean isInitialized() {
        return initAppId != null && AdAdapterRegistry.getInstance().isInited(getSdkType(), initAppId);
    }

    @Override
    public String getSdkName() {
        return getSdkType().getSdkName();
    }

    /**
     * 确保 initAppId 已从 AdSourceConfig 中同步，供 isInitialized() 查询注册表。
     */
    protected void ensureInitAppId(AdSourceConfig sourceConfig) {
        if (this.initAppId == null && sourceConfig != null) {
            String appId = sourceConfig.getAppId();
            if (appId != null && !appId.isEmpty()) {
                this.initAppId = appId;
            }
        }
    }

    /**
     * 模板方法：统一处理 initAppId 同步 + 初始化状态检查，然后委托到子类的 {@link #doRequest}。
     */
    @Override
    public void request(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        if (context == null || sourceConfig == null || callback == null) {
            FsLogger.w(TAG, "request[" + getSdkName() + "]: null params");
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.REQUEST_PARAM_INVALID, FsAdErrorCode.buildMsg("请求参数无效"));
            return;
        }
        ensureInitAppId(sourceConfig);
        if (!isInitialized()) {
            FsLogger.w(TAG, "request[" + getSdkName() + "]: not initialized");
            callback.onLoadFailed(FsAdErrorCode.SDK_NOT_INITIALIZED, FsAdErrorCode.buildMsg("SDK未初始化", getSdkName(), 0, ""));
            return;
        }
        doRequest(context, params, sourceConfig, callback);
    }

    /**
     * 执行格式特定的广告请求逻辑，由子类实现。
     */
    protected abstract void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback);

    @Override
    public boolean supportBidding() {
        return true;
    }

    // ════════════════════════════════════════════════════════════════
    //  SDK 可用性检测
    // ════════════════════════════════════════════════════════════════

    @Override
    public boolean isSdkAvailable() {
        if (sdkAvailable != null) return sdkAvailable;
        try {
            Class.forName("com.qq.e.comm.managers.GDTAdSdk");
            sdkAvailable = true;
        } catch (ClassNotFoundException e) {
            FsLogger.w(TAG, "GDT SDK not found in classpath");
            sdkAvailable = false;
        }
        return sdkAvailable;
    }

    // ════════════════════════════════════════════════════════════════
    //  初始化
    // ════════════════════════════════════════════════════════════════

    @Override
    public void initialize(Context context, String appId, String token, AdInitCallback callback) {
        this.initAppId = appId;
        if (!isSdkAvailable()) {
            FsLogger.w(TAG, "GDT SDK not available, skip initialization");
            callback.onInitFailure(-100, "GDT SDK not found in classpath");
            return;
        }
        try {
            this.appContext = context.getApplicationContext();
            // 统一隐私合规配置
            PrivacyUtils.applyGdtPrivacy();
            // GDT SDK 初始化（GDTAdSdk.init 不支持 appName/debug 参数，仅记录日志）
            FsLogger.d(TAG, "GDT init: appId=" + appId + ", debug=" + SdkUtils.isDebug()
                    + ", appName=" + SdkUtils.resolveAppName(appContext));
            GDTAdSdk.initWithoutStart(context.getApplicationContext(), appId); // 该接口不会采集用户信息
// 调用initWithoutStart后请尽快调用start，否则可能影响广告填充，造成收入下降
            GDTAdSdk.start(new GDTAdSdk.OnStartListener() {
                @Override
                public void onStartSuccess() {
                    // 推荐开发者在onStartSuccess回调后开始拉广告
                    callback.onInitSuccess();
                }

                @Override
                public void onStartFailed(Exception e) {
                    FsLogger.e(TAG, "gdt onStartFailed: " + e.toString());
                    callback.onInitFailure(1001, "init failed");
                }
            });
            FsLogger.d(TAG, "GDT SDK initialized successfully, appId=" + appId);

        } catch (Exception e) {
            FsLogger.e(TAG, "GDT SDK init exception: " + e.getMessage(), e);
            callback.onInitFailure(-1, e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  工具方法
    // ════════════════════════════════════════════════════════════════

    /**
     * 解析广告位 ID
     */
    protected String resolvePlaceId(AdSourceConfig sourceConfig) {
        String placeId = sourceConfig.getAdUnitId();
        if (placeId == null || placeId.isEmpty()) {
            placeId = sourceConfig.getSdkName();
        }
        return placeId;
    }

    /**
     * 构建统一的 UnionAdResponse
     */
    protected UnionAdResponse buildResponse(AdSourceConfig sourceConfig, AdFormat format,
                                            Object nativeAd, int ecpm) {
        return new UnionAdResponse.Builder()
                .sdkName(sourceConfig.getSdkName())
                .adUnitId(sourceConfig.getAdUnitId())
                .sdkType(AdSdkType.GDT)
                .adFormat(format)
                .ecpm(ecpm)
                .expireTimeMs(System.currentTimeMillis() + sourceConfig.getExpireTimeMs())
                .nativeAd(nativeAd)
                .build();
    }

    /**
     * 构建统一的 UnionAdResponse（含 extra 透传数据）
     */
    protected UnionAdResponse buildResponse(AdSourceConfig sourceConfig, AdFormat format,
                                            Object nativeAd, int ecpm,
                                            java.util.Map<String, Object> extra) {
        return new UnionAdResponse.Builder()
                .sdkName(sourceConfig.getSdkName())
                .adUnitId(sourceConfig.getAdUnitId())
                .sdkType(AdSdkType.GDT)
                .adFormat(format)
                .ecpm(ecpm)
                .expireTimeMs(System.currentTimeMillis() + sourceConfig.getExpireTimeMs())
                .nativeAd(nativeAd)
                .extra(extra)
                .build();
    }

    @Override
    public void destroy() {
        // initAppId 是 Adapter 实例字段，不复位——其他 GdtXXXAdapter 实例仍依赖它做 isInitialized() 查询
        appContext = null;
        FsLogger.d(TAG, getClass().getSimpleName() + " destroyed");
    }
}
