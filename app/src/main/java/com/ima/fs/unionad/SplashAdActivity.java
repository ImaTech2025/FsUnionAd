package com.ima.fs.unionad;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.ima.union.core.model.entry.IFsUnionSplashAd;
import com.ima.union.core.model.listener.FsUnionSplashAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.manager.FsSplashAdManager;

/**
 * 开屏广告独立展示页 — Load & Show Splash 完整流程演示。
 *
 * <p>继承 {@link BaseAdActivity}，广告源展示、策略切换、日志面板全部复用基类。</p>
 */
public class SplashAdActivity extends BaseAdActivity {

    private static final String TAG = "Splash";

    // Views
    private FrameLayout mSplashOverlay;
    private TextView mSplashSourceLabel;

    // State
    private IFsUnionSplashAd mSplashAd;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_splash_ad;
    }

    @Override
    protected String getActivityTag() {
        return TAG;
    }

    @Override
    protected AdFormat getAdFormat() {
        return AdFormat.SPLASH;
    }

    @Override
    protected String getSlotId(String sourceKey) {
        switch (sourceKey) {
            case "pangle":  return FsUnionAdApp.PANGLE_SPLASH_ID;
            case "baidu":   return FsUnionAdApp.BAIDU_SPLASH_ID;
            case "custom":  return "custom_splash_slot";
            default:        return FsUnionAdApp.FISSION_SPLASH_ID;
        }
    }

    @Override
    protected String getAppId(String sourceKey) {
        switch (sourceKey) {
            case "pangle":  return FsUnionAdApp.PANGLE_APP_ID;
            case "baidu":   return FsUnionAdApp.BAIDU_APP_ID;
            case "custom":  return "demo_custom_app";
            default:        return FsUnionAdApp.FISSION_APP_ID;
        }
    }

    @Override
    protected String getSlotIdKey() {
        return "10000001";
    }

    @Override
    protected String getDefaultStrategyAssetName() {
        return "ad_strategy_splash.json";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSplashOverlay = findViewById(R.id.splash_overlay);
        mSplashSourceLabel = findViewById(R.id.splash_source_label);

        setupSplashOverlayControls();
        setupActions();

        LogProxy.d(TAG, "SplashAdActivity onCreate");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSplashAd != null) {
            mSplashAd.setListener(null);
            mSplashAd = null;
        }
        LogProxy.d(TAG, "SplashAdActivity onDestroy");
    }

    @Override
    public void onBackPressed() {
        if (mSplashOverlay != null && mSplashOverlay.getVisibility() == View.VISIBLE) {
            hideSplashOverlay();
        } else {
            super.onBackPressed();
        }
    }

    // ── Actions ───────────────────────────────────────────────

    private void setupActions() {
        findViewById(R.id.btn_load_show).setOnClickListener(v -> loadAndShowSplash());
    }

    private void loadAndShowSplash() {
        LogProxy.i(TAG, "========== 开始加载开屏广告 ==========");
        LogProxy.i(TAG, "SlotId: " + getSlotIdKey() + " | Strategy: " + strategyLabel());

        FsSplashAdManager.loadAd(this,
                new AdRequestParams.Builder()
                        .slotId(getSlotIdKey())
                        .defaultStrategyJson(mDefaultStrategyJson)
                        .build(),
                new FsSplashAdManager.OnFsSplashAdLoadListener() {
                    @Override
                    public void onAdLoaded(IFsUnionSplashAd ad) {
                        mSplashAd = ad;
                        LogProxy.i(TAG, "开屏加载成功 → " + ad.getSdkName()
                                + " | ecpm=" + ad.getEcpm()
                                + " | sdk=" + ad.getSdkType().getSdkName());
                        mHandler.post(() -> {
                            mSplashSourceLabel.setText(sourceLabel(ad.getSdkName()));
                            mSplashOverlay.setVisibility(View.VISIBLE);
                            ad.setListener(new FsUnionSplashAdListener() {
                                @Override
                                public void onAdShow(IFsUnionSplashAd ad) {
                                    LogProxy.i(TAG, "开屏展示曝光: " + ad.getSdkName());
                                    refreshLogs();
                                }

                                @Override
                                public void onSplashAdSkipped(IFsUnionSplashAd ad) {
                                    LogProxy.i(TAG, "开屏被跳过: " + ad.getSdkName());
                                    mHandler.post(SplashAdActivity.this::hideSplashOverlay);
                                }

                                @Override
                                public void onAdClick(IFsUnionSplashAd ad) {
                                    LogProxy.i(TAG, "开屏被点击: " + ad.getSdkName());
                                    refreshLogs();
                                }

                                @Override
                                public void onAdClose(IFsUnionSplashAd ad) {
                                    LogProxy.i(TAG, "开屏关闭: " + ad.getSdkName());
                                    mHandler.post(SplashAdActivity.this::hideSplashOverlay);
                                }

                                @Override
                                public void onAdError(IFsUnionSplashAd ad, int code, String msg) {
                                    LogProxy.e(TAG, "开屏出错 [" + code + "]: " + msg);
                                    refreshLogs();
                                }
                            });
                            ad.show(mSplashOverlay);
                        });
                    }

                    @Override
                    public void onAdLoadError(int errorCode, String errorMsg) {
                        LogProxy.e(TAG, "开屏加载失败 [" + errorCode + "]: " + errorMsg);
                        refreshLogs();
                    }
                });
        refreshLogs();
    }

    // ── Splash Overlay ────────────────────────────────────────

    private void setupSplashOverlayControls() {
        findViewById(R.id.btn_skip_splash).setOnClickListener(v -> hideSplashOverlay());
        findViewById(R.id.btn_close_splash).setOnClickListener(v -> hideSplashOverlay());
    }

    private void hideSplashOverlay() {
        if (mSplashOverlay != null) {
            mSplashOverlay.setVisibility(View.GONE);
        }
        LogProxy.i(TAG, "开屏覆盖层已关闭");
    }

    /**
     * 根据 sourceId 反查 source label，用于展示。
     */
    protected String sourceLabel(String sourceId) {
        if (sourceId.contains("pangle")) return "穿山甲 Pangle";
        if (sourceId.contains("baidu"))  return "百青藤 Baidu";
        if (sourceId.contains("custom") || sourceId.contains("demo")) return "自定义 Demo";
        return "飞梭 Fission";
    }
}
