package com.ima.union.adapters.baidu;

import com.ima.union.utils.FsLogger;
import com.ima.union.utils.PrivacyUtils;
import com.ima.union.utils.SdkUtils;

import android.content.Context;

import com.baidu.mobads.sdk.api.BDAdConfig;
import com.baidu.mobads.sdk.api.BiddingListener;
import com.baidu.mobads.sdk.api.ExpressInterstitialAd;
import com.baidu.mobads.sdk.api.ExpressResponse;
import com.baidu.mobads.sdk.api.NativeResponse;
import com.baidu.mobads.sdk.api.RewardVideoAd;
import com.baidu.mobads.sdk.api.SplashAd;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 百青藤（Baidu MobAds）适配器公共基类。
 *
 * <p>提取所有百青藤格式适配器共享的初始化、eCPM 提取、竞价上报、FsUnionListenerBridge 等逻辑。
 * 各格式适配器（Splash/Interstitial/RewardedVideo/FeedTemplate/FeedRender）继承此类并实现对应格式接口。</p>
 *
 * <p>依赖：compileOnly com.baidu:mobads:9.42.2</p>
 */
public abstract class BaiduBaseAdapter implements AdAdapter {

    protected static final String TAG = "BaiduAdapter";

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
        return AdSdkType.BAIDU;
    }

    @Override
    public String getAdapterVersion() {
        return "1.0.0_baidu_9.42.2";
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
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_PARAM_INVALID, FsAdErrorCode.buildMsg("请求参数无效"));
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
            Class.forName("com.baidu.mobads.sdk.api.BDAdConfig");
            sdkAvailable = true;
        } catch (ClassNotFoundException e) {
            FsLogger.w(TAG, "Baidu SDK not found in classpath");
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
            FsLogger.w(TAG, "Baidu SDK not available, skip initialization");
            callback.onInitFailure(-100, "Baidu SDK not found in classpath");
            return;
        }
        try {
            appContext = context.getApplicationContext();
            applyBaiduPrivacy();
            initBaiduSdk(appId, callback);
        } catch (Exception e) {
            FsLogger.e(TAG, "Baidu SDK init failed: " + e.getMessage(), e);
            callback.onInitFailure(-1, e.getMessage());
        }
    }

    /**
     * 统一隐私合规配置，委托给 {@link PrivacyUtils#applyBaiduPrivacy()}。
     */
    private void applyBaiduPrivacy() {
        PrivacyUtils.applyBaiduPrivacy();
    }

    private void initBaiduSdk(String appId, AdInitCallback callback) {
        try {
            String resolvedAppName = SdkUtils.resolveAppName(appContext);
            boolean debug = SdkUtils.isDebug();
            String wxAppid = SdkUtils.resolveWxAppid();
            BDAdConfig.Builder configBuilder = new BDAdConfig.Builder()
                    .setAppsid(appId)
                    .setAppName(resolvedAppName)
                    .setDebug(debug);
            // 微信 OpenSDK 应用 ID（支持微信小程序跳转）
            if (wxAppid != null && !wxAppid.isEmpty()) {
                configBuilder.setWXAppid(wxAppid);
            }
            BDAdConfig bdAdConfig = configBuilder
                    .setBDAdInitListener(new BDAdConfig.BDAdInitListener() {
                        @Override
                        public void success() {
                            callback.onInitSuccess();
                        }

                        @Override
                        public void fail() {
                            callback.onInitFailure(-1, "Baidu SDK init failed");
                        }
                    })
                    .build(appContext);
            bdAdConfig.init();
        } catch (Exception e) {
            callback.onInitFailure(-1, e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  工具方法
    // ════════════════════════════════════════════════════════════════

    protected double getEcpmFromAd(Object adObject) {
        try {
            String ecpmStr = null;
            if (adObject instanceof SplashAd) {
                ecpmStr = ((SplashAd) adObject).getECPMLevel();
            } else if (adObject instanceof ExpressInterstitialAd) {
                ecpmStr = ((ExpressInterstitialAd) adObject).getECPMLevel();
            } else if (adObject instanceof RewardVideoAd) {
                ecpmStr = ((RewardVideoAd) adObject).getECPMLevel();
            } else if (adObject instanceof NativeResponse) {
                ecpmStr = ((NativeResponse) adObject).getECPMLevel();
            } else if (adObject instanceof ExpressResponse) {
                ecpmStr = ((ExpressResponse) adObject).getECPMLevel();
            }
            if (ecpmStr != null && !ecpmStr.isEmpty()) {
                return Double.parseDouble(ecpmStr);
            }
        } catch (Exception e) {
            FsLogger.w(TAG, "Failed to get eCPM: " + e.getMessage());
        }
        return 0;
    }

    protected UnionAdResponse buildResponse(AdSourceConfig sourceConfig, AdFormat format,
                                            Object nativeAd, double ecpm, Map<String, Object> extra) {
        return buildResponse(sourceConfig, format, nativeAd, ecpm, extra, true);
    }

    protected UnionAdResponse buildResponse(AdSourceConfig sourceConfig, AdFormat format,
                                            Object nativeAd, double ecpm, Map<String, Object> extra,
                                            boolean isReady) {
        return new UnionAdResponse.Builder()
                .sdkName(sourceConfig.getSdkName())
                .adUnitId(sourceConfig.getAdUnitId())
                .sdkType(AdSdkType.BAIDU)
                .adFormat(format)
                .ecpm(ecpm)
                .expireTimeMs(System.currentTimeMillis() + sourceConfig.getExpireTimeMs())
                .nativeAd(nativeAd)
                .extra(extra != null ? extra : new HashMap<>())
                .ready(isReady)
                .adapter(this)
                .build();
    }

    /**
     * 读取百度 SDK 广告对象的内置 {@code isReady()} 状态。
     *
     * <p>百度各广告类均提供了 {@code isReady()} 方法：
     * {@code SplashAd.isReady()}、{@code ExpressInterstitialAd.isReady()}、
     * {@code RewardVideoAd.isReady()}（无参），
     * {@code ExpressResponse.isReady(Context)}、{@code NativeResponse.isReady(Context)}（需 Context）。
     * 此方法自动根据实例类型选择对应调用方式。</p>
     *
     * @param nativeAd 百度 SDK 广告对象
     * @return 广告的就绪状态，若无法识别类型则默认返回 {@code true}
     */
    protected boolean readAdIsReady(Object nativeAd) {
        if (nativeAd == null) return true;
        try {
            if (nativeAd instanceof SplashAd) {
                return ((SplashAd) nativeAd).isReady();
            } else if (nativeAd instanceof ExpressInterstitialAd) {
                return ((ExpressInterstitialAd) nativeAd).isReady();
            } else if (nativeAd instanceof RewardVideoAd) {
                return ((RewardVideoAd) nativeAd).isReady();
            } else if (nativeAd instanceof ExpressResponse && appContext != null) {
                return ((ExpressResponse) nativeAd).isReady(appContext);
            } else if (nativeAd instanceof NativeResponse && appContext != null) {
                return ((NativeResponse) nativeAd).isReady(appContext);
            }
        } catch (Exception e) {
            FsLogger.w(TAG, "Failed to read ad isReady: " + e.getMessage());
        }
        return true;
    }

    /**
     * 竞败上报：根据 nativeAd 的具体百度广告类型分发到对应 SDK API。
     *
     * @param nativeAd 百度 SDK 广告对象（SplashAd/ExpressInterstitialAd/...）
     * @param reason   竞败原因，使用 {@link com.ima.union.core.model.BidLossReason} 统一常量
     */
    @Override
    public void reportBidFail(Object nativeAd, String reason) {
        if (nativeAd == null) return;
        try {
            LinkedHashMap<String, Object> winInfo = new LinkedHashMap<>();
            winInfo.put("loss_reason", reason != null ? reason : "LOST");
            if (nativeAd instanceof SplashAd) {
                ((SplashAd) nativeAd).biddingFail(winInfo, EMPTY_BIDDING_LISTENER);
            } else if (nativeAd instanceof ExpressInterstitialAd) {
                ((ExpressInterstitialAd) nativeAd).biddingFail(winInfo, EMPTY_BIDDING_LISTENER);
            } else if (nativeAd instanceof RewardVideoAd) {
                ((RewardVideoAd) nativeAd).biddingFail(winInfo, EMPTY_BIDDING_LISTENER);
            } else if (nativeAd instanceof NativeResponse) {
                ((NativeResponse) nativeAd).biddingFail(winInfo, EMPTY_BIDDING_LISTENER);
            } else if (nativeAd instanceof ExpressResponse) {
                ((ExpressResponse) nativeAd).biddingFail(winInfo, EMPTY_BIDDING_LISTENER);
            }
        } catch (Exception e) {
            FsLogger.w(TAG, "Failed to report bid fail: " + e.getMessage());
        }
    }

    private static final BiddingListener EMPTY_BIDDING_LISTENER = new BiddingListener() {
        @Override
        public void onBiddingResult(boolean success, String message, HashMap<String, Object> extra) {
            // 竞败上报无需关心回调结果
        }
    };

    @Override
    public void destroy() {
        // initAppId 是 Adapter 实例字段，不复位——其他 BaiduXXXAdapter 实例仍依赖它做 isInitialized() 查询
        FsLogger.d(TAG, getClass().getSimpleName() + " destroyed");
    }
}
