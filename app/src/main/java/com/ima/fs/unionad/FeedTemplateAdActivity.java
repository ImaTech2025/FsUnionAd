package com.ima.fs.unionad;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.ima.union.core.model.entry.IFsUnionNativeExpressAd;
import com.ima.union.core.model.listener.FsUnionNativeExpressAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.manager.FsFeedTemplateAdManager;

/**
 * 信息流模板广告独立展示页 — Load Feed Template 完整流程演示。
 *
 * <p>继承 {@link BaseAdActivity}，广告源多选、策略切换、日志面板全部复用基类。</p>
 *
 * <p><b>尺寸配置</b>：UI 提供宽/高输入框（单位 dp），通过
 * {@link AdRequestParams.Builder#setExpressViewAcceptedSize(int, int)}
 * 透传给适配器，适配器在调用 Pangle SDK 时实际执行
 * {@code setExpressViewAcceptedSize}；留空(0) 时不传递给 SDK。</p>
 */
public class FeedTemplateAdActivity extends BaseAdActivity {

    private static final String TAG = "FeedTP";

    private IFsUnionNativeExpressAd mExpressAd;
    private FrameLayout mFeedContainer;
    private View mBtnLoad;
    private TextInputEditText mEtExpressW;
    private TextInputEditText mEtExpressH;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_feed_template;
    }

    @Override
    protected String getActivityTag() {
        return TAG;
    }

    @Override
    protected AdFormat getAdFormat() {
        return AdFormat.FEED_TEMPLATE;
    }

    @Override
    protected String getSlotId(String sourceKey) {
        switch (sourceKey) {
            case "pangle":
                return FsUnionAdApp.PANGLE_FEED_TP_ID;
            case "baidu":
                return FsUnionAdApp.BAIDU_FEED_TP_ID;
            case "custom":
                return "custom_feed_tp_slot";
            default:
                return FsUnionAdApp.FISSION_FEED_TP_ID;
        }
    }

    @Override
    protected String getAppId(String sourceKey) {
        switch (sourceKey) {
            case "pangle":
                return FsUnionAdApp.PANGLE_APP_ID;
            case "baidu":
                return FsUnionAdApp.BAIDU_APP_ID;
            case "custom":
                return "demo_custom_app";
            default:
                return FsUnionAdApp.FISSION_APP_ID;
        }
    }

    @Override
    protected String getSlotIdKey() {
        return "10000004";
    }

    @Override
    protected String getDefaultStrategyAssetName() {
        return "ad_strategy_feed_template.json";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFeedContainer = findViewById(R.id.feed_container);
        mBtnLoad = findViewById(R.id.btn_load);
        mEtExpressW = findViewById(R.id.et_express_w);
        mEtExpressH = findViewById(R.id.et_express_h);
        setupActions();

        LogProxy.d(TAG, "FeedTemplateAdActivity onCreate");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mExpressAd != null) {
            mExpressAd.destroy();
            mExpressAd = null;
        }
        LogProxy.d(TAG, "FeedTemplateAdActivity onDestroy");
    }

    private void setupActions() {
        mBtnLoad.setOnClickListener(v -> loadFeedTemplate());
    }

    private void loadFeedTemplate() {
        LogProxy.i(TAG, "========== 开始加载信息流模板 ==========");
        LogProxy.i(TAG, "SlotId: " + getSlotIdKey() + " | Strategy: " + strategyLabel());

        if (mFeedContainer != null) {
            mFeedContainer.removeAllViews();
        }
        mExpressAd = null;

        // ── 读取尺寸输入框（dp），并按需透传给 AdRequestParams ──
        int expressW = parseEditInt(mEtExpressW, 0);
        int expressH = parseEditInt(mEtExpressH, 0);
        AdRequestParams.Builder paramsBuilder = new AdRequestParams.Builder()
                .slotId(getSlotIdKey())
                .defaultStrategyJson(mDefaultStrategyJson);
        if (expressW > 0 && expressH > 0) {
            paramsBuilder.setExpressViewAcceptedSize(expressW, expressH);
            LogProxy.i(TAG, "setExpressViewAcceptedSize: " + expressW + "x" + expressH + " dp");
        } else {
            LogProxy.w(TAG, "setExpressViewAcceptedSize: 留空(0)，不传递给三方 SDK");
        }

        FsFeedTemplateAdManager.loadAd(this, paramsBuilder.build(), new FsFeedTemplateAdManager.OnFsNativeExpressAdLoadListener() {
            @Override
            public void onAdLoaded(IFsUnionNativeExpressAd ad) {
                mExpressAd = ad;
                LogProxy.i(TAG, "信息流模板加载成功: " + ad.getSdkName()
                        + " ecpm=" + ad.getEcpm());
                ad.setListener(new FsUnionNativeExpressAdListener() {
                    @Override
                    public void onAdShow(IFsUnionNativeExpressAd ad) {
                        LogProxy.i(TAG, "信息流模板曝光: " + ad.getSdkName());
                        refreshLogs();
                    }

                    @Override
                    public void onExpressAdRendered(IFsUnionNativeExpressAd ad) {
                        LogProxy.i(TAG, "信息流模板渲染回调: " + ad.getSdkName());
                        refreshLogs();
                    }

                    @Override
                    public void onAdClick(IFsUnionNativeExpressAd ad) {
                        LogProxy.i(TAG, "信息流模板点击: " + ad.getSdkName());
                        refreshLogs();
                    }

                    @Override
                    public void onAdClose(IFsUnionNativeExpressAd ad) {
                        LogProxy.i(TAG, "信息流模板关闭: " + ad.getSdkName());
                        refreshLogs();
                    }

                    @Override
                    public void onAdError(IFsUnionNativeExpressAd ad, int code, String msg) {
                        LogProxy.e(TAG, "信息流模板错误 [" + code + "]: " + msg);
                        refreshLogs();
                    }
                });
                View adView = ad.getExpressView();
                if (adView != null && mFeedContainer != null) {
                    mHandler.post(() -> {
                        mFeedContainer.removeAllViews();
                        mFeedContainer.addView(adView, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        LogProxy.i(TAG, "模板视图已添加到容器");
                        refreshLogs();
                    });
                } else {
                    LogProxy.w(TAG, "模板 getExpressView() 返回 null");
                    refreshLogs();
                }
            }

            @Override
            public void onAdLoadError(int errorCode, String errorMsg) {
                LogProxy.e(TAG, "信息流模板加载失败 [" + errorCode + "]: " + errorMsg);
                refreshLogs();
            }
        });

        refreshLogs();
    }

    /**
     * 解析 TextInputEditText 的整数内容。空 / 非数字 / &lt;=0 全部返回 fallback。
     */
    private int parseEditInt(TextInputEditText et, int fallback) {
        if (et == null) return fallback;
        String s = et.getText() != null ? et.getText().toString() : "";
        if (TextUtils.isEmpty(s)) return fallback;
        try {
            int v = Integer.parseInt(s.trim());
            return v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
