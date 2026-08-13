package com.ima.union.sample;
import com.ima.union.utils.FsLogger;
import android.app.Application;
import android.content.Context;
import android.view.ViewGroup;

import com.ima.union.BuildConfig;
import com.ima.union.adapters.custom.BaseCustomAdAdapter;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.adapter.AdInitCallback;
import com.ima.union.core.model.entry.IFsUnionRewardedVideoAd;
import com.ima.union.core.model.entry.IFsUnionSplashAd;
import com.ima.union.core.model.listener.FsUnionRewardedVideoAdListener;
import com.ima.union.core.model.listener.FsUnionSplashAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.AdUnitConfig;
import com.ima.union.core.model.StrategyItem;
import com.ima.union.core.model.StrategyType;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.manager.FsRewardedVideoAdManager;
import com.ima.union.manager.FsSplashAdManager;
import com.ima.union.FsUnionSDK;

import java.util.Arrays;

// ── Step 1: Application init ──
class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Aggregate SDK
        FsUnionSDK.initialize(this,
                new FsUnionSDK.Config.Builder()
                        .appId("your_aggregate_app_id")
                        .enableLog(true)
                        .build(),
                success -> FsLogger.i("App", "FsUnionSDK initialized: " + success)
        );

        // Register custom adapter (format-specific)
        FsUnionSDK.registerCustomAdapter(new MyCustomAdAdapter(), AdFormat.SPLASH);
    }
}

// ── Custom adapter example ──
class MyCustomAdAdapter extends BaseCustomAdAdapter {
    private static final AdSdkType AD_SDK_TYPE = AdSdkType.of("MY_SAMPLE", "示例平台");
    private String initAppId;

    @Override public AdSdkType getSdkType() { return AD_SDK_TYPE; }

    /** 必须与策略 JSON 中该广告源的 sdkName 字段完全一致 */
    @Override public String getSdkName() { return "my_custom_source_001"; }
    @Override public String getAdapterVersion() { return "myadnetwork_2.0.0"; }
    /**
     * 统一委托到 AdAdapterRegistry.isInited() 查询。
     * 接入方自定义适配器请按此模式实现，与内置平台适配器保持一致。
     */
    @Override public boolean isInitialized() {
        return initAppId != null && AdAdapterRegistry.getInstance().isInited(getSdkType(), initAppId);
    }

    @Override
    public void initialize(Context context, String appId, String token, AdInitCallback callback) {
        this.initAppId = appId;
        callback.onInitSuccess();
    }

    @Override
    public void request(Context context, AdSourceConfig sourceConfig, AdCallback callback) {
        callback.onLoaded(new UnionAdResponse.Builder()
                .sdkName(sourceConfig.getSdkName())
                .adUnitId(sourceConfig.getAdUnitId())
                .sdkType(AdSdkType.of("MY_NETWORK", "我的广告平台"))
                .adFormat(AdFormat.FEED_TEMPLATE)
                .ecpm(0.0)
                .expireTimeMs(System.currentTimeMillis() + sourceConfig.getExpireTimeMs())
                .nativeAd("my_custom_ad_object")
                .build());
    }

    @Override public void destroy() { this.initAppId = null; }
}

    // ── Ad unit configs ──
// 注:以下 bidFloor / ecpm 等价格字段单位统一为「分」,不再除 100
class AdUnitConfigs {
    static final String FISSION_APP_ID = "TEST";
    static final String FISSION_SLOT = "1_1_1";

    static AdUnitConfig createSplashConfig() {
        // 顶层用 strategies[] 数组描述策略阶段,
        // 单 strategy 项等价于旧版的纯瀑布流 (WATERFALL, timeoutMs=2500)
        StrategyItem waterfall = new StrategyItem.Builder()
                .type(StrategyType.WATERFALL)
                .priority(1)
                .timeoutMs(2500)
                .sources(Arrays.asList(
                        new AdSourceConfig.Builder()
                                .sdkName("fission_splash_001").sdkType(AdSdkType.FISSION)
                                .adFormat(AdFormat.SPLASH).adUnitId(FISSION_SLOT).appId(FISSION_APP_ID)
                                .priority(1).bidFloor(3.0).timeout(5000)
                                .build(),
                        new AdSourceConfig.Builder()
                                .sdkName("pangle_splash_001").sdkType(AdSdkType.PANGLE)
                                .adFormat(AdFormat.SPLASH).adUnitId("tt_splash_unit").appId("pangle_app_id")
                                .priority(2).bidFloor(3.0).timeout(3000)
                                .build(),
                        new AdSourceConfig.Builder()
                                .sdkName("gdt_splash_001").sdkType(AdSdkType.GDT)
                                .adFormat(AdFormat.SPLASH).adUnitId("gdt_splash_unit").appId("gdt_app_id")
                                .priority(3).bidFloor(2.0).timeout(3000)
                                .build(),
                        new AdSourceConfig.Builder()
                                .sdkName("baidu_splash_001").sdkType(AdSdkType.BAIDU)
                                .adFormat(AdFormat.SPLASH).adUnitId("baidu_splash_unit").appId("baidu_app_id")
                                .priority(4).timeout(3000)
                                .build()
                ))
                .build();

        return new AdUnitConfig.Builder()
                .adFormat(AdFormat.SPLASH)
                .strategies(Arrays.asList(waterfall))
                .build();
    }
}

// ── Splash usage example ──
class SplashExample {
    private IFsUnionSplashAd splashAd;
    private ViewGroup splashContainer;

    public void onCreate(Context context, ViewGroup splashContainer) {
        this.splashContainer = splashContainer;

        FsSplashAdManager.loadAd(context, new AdRequestParams.Builder()
                .slotId("10000001")
                .build(), new FsSplashAdManager.OnFsSplashAdLoadListener() {
            @Override
            public void onAdLoaded(IFsUnionSplashAd ad) {
                splashAd = ad;
                FsLogger.d("Splash", "Ad loaded from: " + ad.getSdkName()
                        + " ecpm=" + ad.getEcpm() + " sdk=" + ad.getSdkType().getSdkName());
                ad.setListener(new FsUnionSplashAdListener() {
                    @Override public void onAdShow(IFsUnionSplashAd ad) {
                        FsLogger.d("Splash", "Ad shown: " + ad.getSdkName());
                    }
                    @Override public void onAdClose(IFsUnionSplashAd ad) { navigateToMain(); }
                    @Override public void onSplashAdSkipped(IFsUnionSplashAd ad) { navigateToMain(); }
                    @Override public void onAdError(IFsUnionSplashAd ad, int errorCode, String errorMsg) {
                        FsLogger.e("Splash", "Error: " + errorMsg);
                        navigateToMain();
                    }
                    @Override public void onAdClick(IFsUnionSplashAd ad) {}
                });
                ad.show(splashContainer);
            }
            @Override
            public void onAdLoadError(int errorCode, String errorMsg) {
                FsLogger.e("Splash", "Load failed: " + errorMsg);
                navigateToMain();
            }
        });
    }

    private void navigateToMain() {}
    public void onDestroy() {
        if (splashAd != null) {
            splashAd.setListener(null);
            splashAd = null;
        }
    }
}

// ── Rewarded video usage example ──
class RewardedExample {
    private IFsUnionRewardedVideoAd loadedAd;

    public void onCreate(Context context) {
        FsRewardedVideoAdManager.loadAd(context, new AdRequestParams.Builder()
                .slotId("10000003")
                .build(), new FsRewardedVideoAdManager.OnFsRewardedVideoAdLoadListener() {
            @Override
            public void onAdLoaded(IFsUnionRewardedVideoAd ad) {
                loadedAd = ad;
                FsLogger.d("Rewarded", "Ready: sdkName=" + ad.getSdkName()
                        + " ecpm=" + ad.getEcpm());
                ad.setListener(new FsUnionRewardedVideoAdListener() {
                    @Override public void onAdShow(IFsUnionRewardedVideoAd ad) {}
                    @Override public void onAdClose(IFsUnionRewardedVideoAd ad) {
                        loadedAd = null;
                        // preload next
                        onCreate(context);
                    }
                    @Override public void onRewardVerify(IFsUnionRewardedVideoAd ad, boolean rewardVerify, int amount, String name) {
                        if (rewardVerify) grantReward(amount, name);
                    }
                    @Override public void onAdClick(IFsUnionRewardedVideoAd ad) {}
                    @Override public void onVideoComplete(IFsUnionRewardedVideoAd ad) {}
                    @Override public void onAdError(IFsUnionRewardedVideoAd ad, int errorCode, String errorMsg) {
                        loadedAd = null;
                    }
                });
            }
            @Override
            public void onAdLoadError(int errorCode, String errorMsg) {
                FsLogger.e("Rewarded", "Load failed: " + errorMsg);
            }
        });
    }

    public void onButtonClick() {
        if (loadedAd != null) loadedAd.show();
    }
    private void grantReward(int amount, String name) {}
    public void onDestroy() {
        if (loadedAd != null) {
            loadedAd.setListener(null);
            loadedAd = null;
        }
    }
}
