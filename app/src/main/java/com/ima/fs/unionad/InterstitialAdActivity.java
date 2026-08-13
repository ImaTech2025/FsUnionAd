package com.ima.fs.unionad;

import android.os.Bundle;
import android.view.View;

import com.ima.union.core.model.entry.IFsUnionInterstitialAd;
import com.ima.union.core.model.listener.FsUnionInterstitialAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.manager.FsInterstitialAdManager;

/**
 * 插屏广告独立展示页 — Load + Show Interstitial 完整流程演示。
 *
 * <p>继承 {@link BaseAdActivity}，广告源展示、策略切换、日志面板全部复用基类。</p>
 */
public class InterstitialAdActivity extends BaseAdActivity {

    private static final String TAG = "Interstitial";

    private IFsUnionInterstitialAd mInterAd;
    private View mBtnLoad, mBtnShow;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_interstitial_ad;
    }

    @Override
    protected String getActivityTag() {
        return TAG;
    }

    @Override
    protected AdFormat getAdFormat() {
        return AdFormat.INTERSTITIAL;
    }

    @Override
    protected String getSlotId(String sourceKey) {
        switch (sourceKey) {
            case "pangle":  return FsUnionAdApp.PANGLE_INTER_ID;
            case "baidu":   return FsUnionAdApp.BAIDU_INTER_ID;
            case "custom":  return "custom_inter_slot";
            default:        return FsUnionAdApp.FISSION_INTER_ID;
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
        return "10000002";
    }

    @Override
    protected String getDefaultStrategyAssetName() {
        return "ad_strategy_interstitial.json";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBtnLoad = findViewById(R.id.btn_load);
        mBtnShow = findViewById(R.id.btn_show);
        setupActions();

        LogProxy.d(TAG, "InterstitialAdActivity onCreate");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mInterAd != null) {
            mInterAd.setListener(null);
            mInterAd = null;
        }
        LogProxy.d(TAG, "InterstitialAdActivity onDestroy");
    }

    private void setupActions() {
        mBtnLoad.setOnClickListener(v -> loadInterstitial());
        mBtnShow.setOnClickListener(v -> {
            if (mInterAd != null) {
                LogProxy.i(TAG, "展示插屏广告... sourceId=" + mInterAd.getSdkName()
                        + " ecpm=" + mInterAd.getEcpm());
                mInterAd.show();
            } else {
                LogProxy.w(TAG, "插屏未就绪，请先 Load");
                refreshLogs();
            }
        });
    }

    private void loadInterstitial() {
        LogProxy.i(TAG, "========== 开始加载插屏广告 ==========");
        LogProxy.i(TAG, "SlotId: " + getSlotIdKey() + " | Strategy: " + strategyLabel());

        mInterAd = null;
        FsInterstitialAdManager.loadAd(this,
                new AdRequestParams.Builder()
                        .slotId(getSlotIdKey())
                        .defaultStrategyJson(mDefaultStrategyJson)
                        .build(),
                new FsInterstitialAdManager.OnFsInterstitialAdLoadListener() {
                    @Override
                    public void onAdLoaded(IFsUnionInterstitialAd ad) {
                        mInterAd = ad;
                        LogProxy.i(TAG, "插屏加载成功: " + ad.getSdkName()
                                + " ecpm=" + ad.getEcpm());
                        ad.setListener(new FsUnionInterstitialAdListener() {
                            @Override public void onAdShow(IFsUnionInterstitialAd ad) {
                                LogProxy.i(TAG, "插屏展示曝光: " + ad.getSdkName());
                                refreshLogs();
                            }
                            @Override public void onAdClose(IFsUnionInterstitialAd ad) {
                                LogProxy.i(TAG, "插屏已关闭: " + ad.getSdkName());
                                mInterAd = null;
                                refreshLogs();
                            }
                            @Override public void onAdClick(IFsUnionInterstitialAd ad) {
                                LogProxy.i(TAG, "插屏点击: " + ad.getSdkName());
                                refreshLogs();
                            }
                            @Override public void onAdError(IFsUnionInterstitialAd ad, int code, String msg) {
                                LogProxy.e(TAG, "插屏出错 [" + code + "]: " + msg);
                                mInterAd = null;
                                refreshLogs();
                            }
                        });
                        refreshLogs();
                    }

                    @Override
                    public void onAdLoadError(int errorCode, String errorMsg) {
                        LogProxy.e(TAG, "插屏加载失败 [" + errorCode + "]: " + errorMsg);
                        mInterAd = null;
                        refreshLogs();
                    }
                });
        refreshLogs();
    }
}
