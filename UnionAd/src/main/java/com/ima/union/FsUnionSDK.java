package com.ima.union;

import android.content.Context;

import com.ima.union.BuildConfig;
import com.ima.union.utils.FsUnionSdkVersion;
import com.ima.union.adapters.baidu.BaiduFeedRenderAdapter;
import com.ima.union.adapters.baidu.BaiduFeedTemplateAdapter;
import com.ima.union.adapters.baidu.BaiduInterstitialAdapter;
import com.ima.union.adapters.baidu.BaiduRewardedVideoAdapter;
import com.ima.union.adapters.baidu.BaiduSplashAdapter;
import com.ima.union.adapters.fission.FissionFeedRenderAdapter;
import com.ima.union.adapters.fission.FissionFeedTemplateAdapter;
import com.ima.union.adapters.fission.FissionInterstitialAdapter;
import com.ima.union.adapters.fission.FissionRewardedVideoAdapter;
import com.ima.union.adapters.fission.FissionSplashAdapter;
import com.ima.union.adapters.gdt.GdtFeedRenderAdapter;
import com.ima.union.adapters.gdt.GdtFeedTemplateAdapter;
import com.ima.union.adapters.gdt.GdtInterstitialAdapter;
import com.ima.union.adapters.gdt.GdtRewardedVideoAdapter;
import com.ima.union.adapters.gdt.GdtSplashAdapter;
import com.ima.union.adapters.pangle.PangleFeedRenderAdapter;
import com.ima.union.adapters.pangle.PangleFeedTemplateAdapter;
import com.ima.union.adapters.pangle.PangleInterstitialAdapter;
import com.ima.union.adapters.pangle.PangleRewardedVideoAdapter;
import com.ima.union.adapters.pangle.PangleSplashAdapter;
import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.config.CloudStrategyManager;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.FsUnionPrivacyConfig;
import com.ima.union.utils.FsLogger;

public class FsUnionSDK {

    private static final String TAG = "FsUnionSDK";
    private static boolean initialized = false;
    private static Config globalConfig;

    public static class Config {
        public final String appId;
        public final String appName;
        public final boolean enableLog;
        public final boolean enableMediationReport;
        public final boolean debug;
        public final String wxAppid;
        public final String reportUrl;
        public final String cloudConfigUrl;
        public final FsUnionPrivacyConfig privacyConfig;

        private Config(Builder builder) {
            this.appId = builder.appId;
            this.appName = builder.appName;
            this.enableLog = builder.enableLog;
            this.enableMediationReport = builder.enableMediationReport;
            this.debug = builder.debug;
            this.wxAppid = builder.wxAppid;
            this.reportUrl = builder.reportUrl;
            this.cloudConfigUrl = builder.cloudConfigUrl;
            this.privacyConfig = builder.privacyConfig;
        }

        public static class Builder {
            private String appId;
            private String appName;
            private boolean enableLog = false;
            private boolean enableMediationReport = true;
            private boolean debug = false;
            private String wxAppid = "";
            private String reportUrl = "";
            private String cloudConfigUrl = "";
            private FsUnionPrivacyConfig privacyConfig;

            public Builder appId(String appId) {
                this.appId = appId;
                return this;
            }

            /**
             * 业务方应用名称，会透传给 Pangle/飞梭等三方 SDK 用于后台展示。
             * 不传时由聚合内部通过 PackageManager.getApplicationLabel() 反射获取。
             */
            public Builder appName(String appName) {
                this.appName = appName;
                return this;
            }

            public Builder enableLog(boolean enableLog) {
                this.enableLog = enableLog;
                return this;
            }

            public Builder enableMediationReport(boolean enableMediationReport) {
                this.enableMediationReport = enableMediationReport;
                return this;
            }

            /**
             * 三方 SDK 调试模式开关（Pangle/飞梭）。建议绑定 BuildConfig.DEBUG。
             */
            public Builder debug(boolean debug) {
                this.debug = debug;
                return this;
            }

            /**
             * 微信 OpenSDK 应用 ID，会透传给百度等三方 SDK 用于微信小程序跳转。
             * 不传则忽略（空字符串）。
             */
            public Builder wxAppid(String wxAppid) {
                this.wxAppid = wxAppid;
                return this;
            }

            /**
             * 隐私合规配置，统一控制各 SDK 的设备 ID / 位置 / 存储权限
             * 及个性化广告限制。不传则使用默认值（全部授权，不限个性化）。
             */
            public Builder privacyConfig(FsUnionPrivacyConfig privacyConfig) {
                this.privacyConfig = privacyConfig;
                return this;
            }

            public Builder reportUrl(String reportUrl) {
                this.reportUrl = reportUrl;
                return this;
            }

            /**
             * 云端配置下发地址（可选）
             */
            public Builder cloudConfigUrl(String cloudConfigUrl) {
                this.cloudConfigUrl = cloudConfigUrl;
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }

    public interface InitCallback {
        void onComplete(boolean success);
    }

    public static void initialize(Context context, Config config, InitCallback onComplete) {
        FsLogger.setDebug(config.enableLog);
        if (initialized) {
            FsLogger.w(TAG, "FsUnionSDK already initialized");
            if (onComplete != null) onComplete.onComplete(true);
            return;
        }

        globalConfig = config;
        AdAdapterRegistry registry = AdAdapterRegistry.getInstance();

        // ── 构造各平台适配器并注册 ──────────────────────────────────
        // appName/debug 不再逐 adapter 手动注入，各适配器通过 SdkUtils
        // 从 Config 中统一获取（FsUnionSDK.getGlobalConfig()）

        // --- 穿山甲 (已按格式拆分) ---
        registry.register(new PangleSplashAdapter(), AdFormat.SPLASH);
        registry.register(new PangleInterstitialAdapter(), AdFormat.INTERSTITIAL);
        registry.register(new PangleRewardedVideoAdapter(), AdFormat.REWARDED_VIDEO);
        registry.register(new PangleFeedTemplateAdapter(), AdFormat.FEED_TEMPLATE);
        registry.register(new PangleFeedRenderAdapter(), AdFormat.FEED_RENDER);

        // --- 优量汇 (已按格式拆分) ---
        registry.register(new GdtSplashAdapter(), AdFormat.SPLASH);
        registry.register(new GdtInterstitialAdapter(), AdFormat.INTERSTITIAL);
        registry.register(new GdtRewardedVideoAdapter(), AdFormat.REWARDED_VIDEO);
        registry.register(new GdtFeedRenderAdapter(), AdFormat.FEED_RENDER);
        registry.register(new GdtFeedTemplateAdapter(), AdFormat.FEED_TEMPLATE);

        // --- 百青藤 (已按格式拆分) ---
        registry.register(new BaiduSplashAdapter(), AdFormat.SPLASH);
        registry.register(new BaiduInterstitialAdapter(), AdFormat.INTERSTITIAL);
        registry.register(new BaiduRewardedVideoAdapter(), AdFormat.REWARDED_VIDEO);
        registry.register(new BaiduFeedTemplateAdapter(), AdFormat.FEED_TEMPLATE);
        registry.register(new BaiduFeedRenderAdapter(), AdFormat.FEED_RENDER);

        // --- 飞梭 (已按格式拆分) ---
        registry.register(new FissionSplashAdapter(), AdFormat.SPLASH);
        registry.register(new FissionInterstitialAdapter(), AdFormat.INTERSTITIAL);
        registry.register(new FissionRewardedVideoAdapter(), AdFormat.REWARDED_VIDEO);
        registry.register(new FissionFeedTemplateAdapter(), AdFormat.FEED_TEMPLATE);
        registry.register(new FissionFeedRenderAdapter(), AdFormat.FEED_RENDER);

        // ── 初始化云配置管理器（支持云配置下发） ──
        CloudStrategyManager.getInstance().initialize(
                context.getApplicationContext(),
                config.cloudConfigUrl);

        // ── 仅注册适配器，不自动初始化各平台 SDK ──
        // 各平台的 appId 不同，由策略引擎在执行广告请求前通过 ensureInitialized() 自动触发。
        initialized = true;
        FsLogger.i(TAG, "FsUnionSDK initialized (Java), version: " + BuildConfig.SDK_VERSION
                + ", " + registry.getAllAdapters().size() + " adapters registered");
        if (onComplete != null) onComplete.onComplete(true);
    }

    /**
     * 注册自定义适配器（格式特定）。
     *
     * <p>Key 规则与内置平台一致：{@code "{sdkType.getKey()}_{format}"},
     * 如 {@code CUSTOM_SPLASH}、{@code MYNETWORK_INTERSTITIAL}。</p>
     *
     * @param adapter 自定义适配器实例
     * @param format  接入的广告格式（如 SPLASH）
     */
    public static void registerCustomAdapter(AdAdapter adapter, AdFormat format) {
        checkInitialized();
        AdAdapterRegistry.getInstance().registerCustomAdapter(adapter, format);
        FsLogger.i("FsUnionSDK", "Custom adapter registered: " + adapter.getAdapterVersion() + " for " + format);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取当前集成 SDK 的版本号字符串,例如 "1.0.0-java"。
     * 转发自 {@link FsUnionSdkVersion#getVersion()},不要求 SDK 已初始化。
     */
    public static String getVersion() {
        return FsUnionSdkVersion.getVersion();
    }

    /**
     * 获取当前集成 SDK 的整型构建号。
     * 转发自 {@link FsUnionSdkVersion#getVersionCode()},不要求 SDK 已初始化。
     */
    public static int getVersionCode() {
        return FsUnionSdkVersion.getVersionCode();
    }

    public static Config getGlobalConfig() {
        return globalConfig;
    }

    public static CloudStrategyManager getCloudStrategyManager() {
        return CloudStrategyManager.getInstance();
    }

    public static void destroy() {
        AdAdapterRegistry.getInstance().clear();
        initialized = false;
    }

    private static void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("FsUnionSDK is not initialized. Call FsUnionSDK.initialize() first.");
        }
    }
}
