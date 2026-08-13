package com.ima.union.adapters.fission;

import com.ima.union.utils.FsLogger;
import com.ima.union.utils.PrivacyUtils;
import com.ima.union.utils.SdkUtils;
import android.content.Context;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.adapter.AdInitCallback;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.FsAdErrorCode;

import com.zm.fissionsdk.api.FissionConfig;
import com.zm.fissionsdk.api.interfaces.FissionConstant;
import com.zm.fissionsdk.api.FissionSdk;
import com.zm.fissionsdk.api.FissionSensitivityController;
import com.zm.fissionsdk.api.FissionSlot;
import com.zm.fissionsdk.api.interfaces.IFissionLoginListener;
import com.zm.fissionsdk.api.interfaces.IFissionRuntime;
import com.zm.fissionsdk.api.interfaces.IFission;

/**
 * 飞梭（Fission）适配器公共基类。
 *
 * <p>提取所有飞梭格式适配器共享的初始化、Slot 构建、工具方法等逻辑。
 * 各格式适配器（Splash/Interstitial/RewardedVideo/FeedTemplate/FeedRender）继承此类并实现对应格式接口。</p>
 *
 * <p>依赖：compileOnly Fission SDK v1.0.99.06</p>
 */
public abstract class FissionBaseAdapter implements AdAdapter {

    protected static final String TAG = "FissionAdAdapter";
    public static final String VERSION = "1.0.99.06";

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
        return AdSdkType.FISSION;
    }

    @Override
    public String getAdapterVersion() {
        return VERSION;
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
     *
     * <p>当 split adapter 模式下，{@link #initialize} 只在一个实例上被调用，
     * 其他同 SDK 类型的 adapter 实例的 {@code initAppId} 可能仍为 null。
     * 在 {@link #request} 模板方法中自动调用，避免误判「未初始化」。</p>
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

    public boolean isSdkAvailable() {
        if (sdkAvailable != null) return sdkAvailable;
        try {
            Class.forName("com.zm.fissionsdk.api.FissionSdk");
            sdkAvailable = true;
        } catch (ClassNotFoundException e) {
            FsLogger.w(TAG, "Fission SDK not found in classpath");
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
            FsLogger.w(TAG, "Fission SDK not available, skip initialization");
            callback.onInitFailure(-100, "Fission SDK not found in classpath");
            return;
        }
        this.appContext = context.getApplicationContext();

        FsLogger.d(TAG, "Initializing with appId=" + appId + ", token=" + token + "...");

        String resolvedAppName = SdkUtils.resolveAppName(context);
        boolean debug = SdkUtils.isDebug();

        FissionConfig config = new FissionConfig.Builder()
                .setToken(token)
                .setAppId(appId)
                .setAppName(resolvedAppName)
                .setDebug(debug)
                .setAllowShowNotification(true)
                .setShowDownloadToast(true)
                .setFissionRuntime(createDefaultRuntime())
                .setSensitivityController(PrivacyUtils.createFissionSensitivityController())
                .addGlobalConfig(FissionConfig.PERSONAL_RECOMMEND, PrivacyUtils.getFissionPersonalRecommend())
                .addGlobalConfig(FissionConfig.SENSOR_ENABLE, true)
                .build();

        FissionSdk.init(context.getApplicationContext(), config, new FissionSdk.InitCallback() {
            @Override
            public void onSuccess() {
                FsLogger.i(TAG, "Fission SDK initialized successfully");
                callback.onInitSuccess();
            }

            @Override
            public void onFailed(int code, String msg) {
                FsLogger.e(TAG, "Fission SDK init failed: [" + code + "] " + msg);
                callback.onInitFailure(code, msg);
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  工具方法
    // ════════════════════════════════════════════════════════════════

    /**
     * 记录业务方传入的尺寸（仅日志）。Fission SDK 公开 API 不支持原生尺寸字段。
     */
    protected void logRequestedSizeIfPresent(String tag, AdRequestParams params) {
        if (params == null) return;
        int[] expressSize = params.getExpressViewAcceptedSize();
        int[] imageSize = params.getImageAcceptedSize();
        if (expressSize != null) {
            FsLogger.d(tag + ": expressViewSize=" + expressSize[0] + "x" + expressSize[1]
                    + " dp (NOT passed to Fission SDK: no native size API)");
        }
        if (imageSize != null) {
            FsLogger.d(tag + ": imageSize=" + imageSize[0] + "x" + imageSize[1]
                    + " px (NOT passed to Fission SDK: no native size API)");
        }
    }

    protected FissionSlot buildSlot(Context context, AdSourceConfig sourceConfig, boolean videoMuted) {
        FissionSlot.Builder builder = new FissionSlot.Builder()
                .setContext(context)
                .setSlotId(sourceConfig.getAdUnitId())
                .setCount(1);

        switch (sourceConfig.getAdFormat()) {
            case SPLASH:
                builder.setSlotType(FissionConstant.SLOT_TYPE_SPLASH);
                break;
            case INTERSTITIAL:
                builder.setSlotType(FissionConstant.SLOT_TYPE_INTERSTITIAL);
                break;
            case REWARDED_VIDEO:
                builder.setSlotType(FissionConstant.SLOT_TYPE_REWARD_VIDEO);
                break;
            case FEED_TEMPLATE:
                builder.setSlotType(FissionConstant.SLOT_TYPE_NATIVE);
                builder.setExpressType(FissionConstant.NATIVE_EXPRESS_TYPE_LARGE);
                break;
            case FEED_RENDER:
                builder.setSlotType(FissionConstant.SLOT_TYPE_NATIVE);
                break;
        }

        // 透传静音配置
        builder.addRequestParam(FissionSlot.VIDEO_MUTE, videoMuted);

        return builder.build();
    }

    /**
     * 构建统一的 UnionAdResponse
     */
    protected UnionAdResponse buildResponse(AdSourceConfig sourceConfig, AdFormat format,
                                            Object nativeAd, double ecpm) {
        return new UnionAdResponse.Builder()
                .sdkName(sourceConfig.getSdkName())
                .adUnitId(sourceConfig.getAdUnitId())
                .sdkType(AdSdkType.FISSION)
                .adFormat(format)
                .ecpm(ecpm)
                .expireTimeMs(System.currentTimeMillis() + sourceConfig.getExpireTimeMs())
                .nativeAd(nativeAd)
                .adapter(this)
                .build();
    }

    protected IFissionRuntime createDefaultRuntime() {
        return new IFissionRuntime() {
            @Override
            public int getDeviceType() {
                return FissionConstant.DEVICE_TYPE_PHONE;
            }

            @Override
            public String getOAid() {
                return "";
            }

            @Override
            public String getImei() {
                return "";
            }

            @Override
            public String getAndroidId() {
                return "";
            }

            @Override
            public String getMac() {
                return "";
            }

            @Override
            public int getNetworkType() {
                return FissionConstant.NET_TYPE_WIFI;
            }

            @Override
            public int getCarrier() {
                return FissionConstant.CARRIER_MOBILE;
            }

            @Override
            public double getLongitude() {
                return 0.0;
            }

            @Override
            public double getLatitude() {
                return 0.0;
            }

            @Override
            public String getUid() {
                return "";
            }

            @Override
            public boolean isLogin() {
                return false;
            }

            @Override
            public void toLogin(IFissionLoginListener listener) {
                listener.onLoginFail();
            }
        };
    }

    protected FissionSensitivityController createSensitivityController() {
        return PrivacyUtils.createFissionSensitivityController();
    }

    /**
     * 竞败上报：通过飞梭 SDK 的 {@code onBidFail(ecpm, reason)} API 通知竞价落败。
     *
     * @param nativeAd 飞梭广告对象（IFission）
     * @param reason   竞败原因，使用 {@link com.ima.union.core.model.BidLossReason} 统一常量
     */
    @Override
    public void reportBidFail(Object nativeAd, String reason) {
        if (nativeAd instanceof IFission) {
            try {
                IFission fissionAd = (IFission) nativeAd;
                int ecpm = fissionAd.getECpm();
                fissionAd.onBidFail(String.valueOf(ecpm), reason);
            } catch (Exception e) {
                FsLogger.w(TAG, "Failed to report bid fail: " + e.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        // initAppId 是 Adapter 实例字段，不复位——其他 FissionXXXAdapter 实例仍依赖它做 isInitialized() 查询
        FsLogger.d(TAG, getClass().getSimpleName() + " destroyed");
    }
}
