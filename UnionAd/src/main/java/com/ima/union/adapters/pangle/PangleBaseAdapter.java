package com.ima.union.adapters.pangle;

import com.ima.union.utils.FsLogger;
import com.ima.union.utils.PrivacyUtils;
import com.ima.union.utils.SdkUtils;
import android.content.Context;
import android.util.DisplayMetrics;

import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdConfig;
import com.bytedance.sdk.openadsdk.TTAdLoadType;
import com.bytedance.sdk.openadsdk.TTAdManager;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTAdSdk;
import com.bytedance.sdk.openadsdk.TTNativeAd;
import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.adapter.AdInitCallback;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;

import java.util.Map;

/**
 * 穿山甲（Pangle/CSJ）适配器公共基类。
 *
 * <p>提取所有穿山甲格式适配器共享的初始化、eCPM 提取、竞价上报、AdSlot 构建等逻辑。
 * 各格式适配器（Splash/Interstitial/RewardedVideo/FeedTemplate/FeedRender）继承此类并实现对应格式接口。</p>
 *
 * <p>依赖：compileOnly com.pangle.cn:ads-sdk-pro:7.6.1.2</p>
 */
public abstract class PangleBaseAdapter implements AdAdapter {

    protected static final String TAG = "PangleAdapter";

    /** 初始化 appId（用于 isInitialized() 委托到 AdAdapterRegistry.isInited()） */
    protected volatile String initAppId;
    protected Context appContext;
    protected Boolean sdkAvailable = null;

    // ════════════════════════════════════════════════════════════════
    //  基础信息
    // ════════════════════════════════════════════════════════════════

    @Override
    public AdSdkType getSdkType() {
        return AdSdkType.PANGLE;
    }

    @Override
    public String getAdapterVersion() {
        return "1.0.0_pangle_7.6.1.2_direct";
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
            if (callback != null) callback.onLoadFailed(FsAdErrorCode.REQUEST_PARAM_INVALID,
                    FsAdErrorCode.buildMsg("请求参数无效"));
            return;
        }
        ensureInitAppId(sourceConfig);
        if (!isInitialized()) {
            FsLogger.w(TAG, "request[" + getSdkName() + "]: not initialized");
            callback.onLoadFailed(FsAdErrorCode.SDK_NOT_INITIALIZED,
                    FsAdErrorCode.buildMsg("SDK未初始化", getSdkName(), 0, ""));
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
            Class.forName("com.bytedance.sdk.openadsdk.TTAdSdk");
            sdkAvailable = true;
        } catch (ClassNotFoundException e) {
            FsLogger.w(TAG, "Pangle SDK not found in classpath");
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
            FsLogger.w(TAG, "Pangle SDK not available, skip initialization");
            callback.onInitFailure(-100, "Pangle SDK not found in classpath");
            return;
        }
        try {
            this.appContext = context.getApplicationContext();
            TTAdConfig config = buildPangleConfig(appId);
            if (config == null) {
                callback.onInitFailure(-101, "Failed to build Pangle config");
                return;
            }
            boolean initResult = TTAdSdk.init(context, config);
            FsLogger.d(TAG, "TTAdSdk.init() returned: " + initResult);
            if (!initResult) {
                callback.onInitFailure(-101, "TTAdSdk.init() returned false");
                return;
            }
            TTAdSdk.start(new TTAdSdk.Callback() {
                @Override
                public void success() {
                    FsLogger.d(TAG, "Pangle SDK init success, appId=" + appId);
                    callback.onInitSuccess();
                }

                @Override
                public void fail(int code, String msg) {
                    FsLogger.e(TAG, "Pangle SDK init failed: code=" + code + " msg=" + msg);
                    callback.onInitFailure(code, msg);
                }
            });
        } catch (Exception e) {
            FsLogger.e(TAG, "Pangle init exception: " + e.getMessage(), e);
            callback.onInitFailure(-1, e.getMessage());
        }
    }

    private TTAdConfig buildPangleConfig(String appId) {
        try {
            String resolvedAppName = SdkUtils.resolveAppName(appContext);
            boolean debug = SdkUtils.isDebug();
            return new TTAdConfig.Builder()
                    .appId(appId)
                    .appName(resolvedAppName)
                    .allowShowNotify(true)
                    .debug(debug)
                    .supportMultiProcess(false)
                    .customController(PrivacyUtils.createPangleCustomController())
                    .build();
        } catch (Exception e) {
            FsLogger.e(TAG, "buildPangleConfig failed: " + e.getMessage(), e);
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  工具方法
    // ════════════════════════════════════════════════════════════════

    /**
     * 解析广告位 codeId：优先 adUnitId，为空时回退 sdkName
     */
    protected String resolveCodeId(AdSourceConfig sourceConfig) {
        return sourceConfig.getAdUnitId();
    }

    /**
     * 创建 TTAdNative 实例
     */
    protected TTAdNative createTtAdNative(Context context) {
        try {
            TTAdManager manager = TTAdSdk.getAdManager();
            return manager.createAdNative(context);
        } catch (Exception e) {
            FsLogger.e(TAG, "createTtAdNative failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建 AdSlot。
     *
     * @param format 广告格式，影响 slot 参数配置
     * @param imgW   图片默认宽度（px），仅 FEED_RENDER 且外部未传 imageSize 时使用
     * @param imgH   图片默认高度（px），同上
     * @param params 请求参数（外部可传入尺寸覆盖默认值）
     */
    protected AdSlot buildAdSlot(String codeId, AdFormat format, int imgW, int imgH, AdRequestParams params) {
        AdSlot.Builder builder = new AdSlot.Builder()
                .setCodeId(codeId)
                .setAdLoadType(TTAdLoadType.PRELOAD);

        if (format == AdFormat.SPLASH) {
            float screenW = 360f, screenH = 640f;
            int[] expressSize = params != null ? params.getExpressViewAcceptedSize() : null;
            if (expressSize != null) {
                screenW = expressSize[0];
                screenH = expressSize[1];
            } else if (appContext != null) {
                try {
                    DisplayMetrics dm = appContext.getResources().getDisplayMetrics();
                    screenW = dm.widthPixels / dm.density;
                    screenH = dm.heightPixels / dm.density;
                } catch (Exception ignored) {
                }
            }
            builder.setExpressViewAcceptedSize(screenW, screenH);
        } else if (format == AdFormat.FEED_TEMPLATE) {
            int[] expressSize = params != null ? params.getExpressViewAcceptedSize() : null;
            if (expressSize != null) {
                builder.setExpressViewAcceptedSize(expressSize[0], expressSize[1]);
            }
            builder.setAdCount(1);
        } else if (format == AdFormat.FEED_RENDER) {
            int[] expressSize = params != null ? params.getExpressViewAcceptedSize() : null;
            int[] imageSize = params != null ? params.getImageAcceptedSize() : null;
            if (imageSize != null) {
                builder.setImageAcceptedSize(imageSize[0], imageSize[1]);
            } else {
                builder.setImageAcceptedSize(imgW, imgH);
            }
            if (expressSize != null) {
                builder.setExpressViewAcceptedSize(expressSize[0], expressSize[1]);
            }
            builder.setAdCount(1);
        }
        return builder.build();
    }

    /**
     * 从穿山甲广告对象中获取 eCPM（单位：分）。
     * 通过 Pangle SDK 公开 API getMediaExtraInfo().get("price") 获取。
     */
    protected double getEcpmFromAd(Object ad) {
        if (ad == null) return 0.0;
        try {
            TTNativeAd nativeAd = (TTNativeAd) ad;
            Map<String, Object> map = nativeAd.getMediaExtraInfo();
            if (map == null) return 0.0;
            Object price = map.get("price");
            if (price instanceof Number) {
                return ((Number) price).doubleValue();
            }
        } catch (Exception e) {
            FsLogger.w(TAG, "getMediaExtraInfo().get(\"price\") failed: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * 构建统一的 UnionAdResponse
     */
    protected UnionAdResponse buildResponse(AdSourceConfig sourceConfig, AdFormat format,
                                            Object nativeAd, double ecpm) {
        return new UnionAdResponse.Builder()
                .sdkName(sourceConfig.getSdkName())
                .adUnitId(sourceConfig.getAdUnitId())
                .sdkType(AdSdkType.PANGLE)
                .adFormat(format)
                .ecpm(ecpm)
                .expireTimeMs(System.currentTimeMillis() + sourceConfig.getExpireTimeMs())
                .nativeAd(nativeAd)
                .build();
    }

    @Override
    public void destroy() {
        // initAppId 是 Adapter 实例字段，不复位——其他 PangleXXXAdapter 实例仍依赖它做 isInitialized() 查询
        appContext = null;
        FsLogger.d(TAG, getClass().getSimpleName() + " destroyed");
    }
}
