package com.ima.fs.unionad;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.ima.union.core.model.entry.IFsUnionRewardedVideoAd;
import com.ima.union.core.model.listener.FsUnionRewardedVideoAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.manager.FsRewardedVideoAdManager;

import java.util.Locale;

/**
 * 激励视频独立展示页 — Load + Show Rewarded Video 完整流程演示。
 *
 * <p>继承 {@link BaseAdActivity}，广告源多选、策略切换、日志面板全部复用基类。</p>
 */
public class RewardedVideoAdActivity extends BaseAdActivity {

    private static final String TAG = "Rewarded";

    private IFsUnionRewardedVideoAd mRewardAd;
    private View mBtnLoad, mBtnShow;
    private TextView mRewardStatus;
    private int mRewardCount = 0;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_rewarded_ad;
    }

    @Override
    protected String getActivityTag() {
        return TAG;
    }

    @Override
    protected AdFormat getAdFormat() {
        return AdFormat.REWARDED_VIDEO;
    }

    @Override
    protected String getSlotId(String sourceKey) {
        switch (sourceKey) {
            case "pangle":  return FsUnionAdApp.PANGLE_REWARD_ID;
            case "baidu":   return FsUnionAdApp.BAIDU_REWARD_ID;
            case "custom":  return "custom_reward_slot";
            default:        return FsUnionAdApp.FISSION_REWARD_ID;
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
        return "10000003";
    }

    @Override
    protected String getDefaultStrategyAssetName() {
        return "ad_strategy_rewarded.json";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBtnLoad = findViewById(R.id.btn_load);
        mBtnShow = findViewById(R.id.btn_show);
        mRewardStatus = findViewById(R.id.reward_status_text);
        setupActions();

        LogProxy.d(TAG, "RewardedVideoAdActivity onCreate");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRewardAd != null) {
            mRewardAd.setListener(null);
            mRewardAd = null;
        }
        LogProxy.d(TAG, "RewardedVideoAdActivity onDestroy");
    }

    private void setupActions() {
        mBtnLoad.setOnClickListener(v -> loadRewardedVideo());
        mBtnShow.setOnClickListener(v -> {
            if (mRewardAd != null) {
                LogProxy.i(TAG, "展示激励视频... sourceId=" + mRewardAd.getSdkName()
                        + " ecpm=" + mRewardAd.getEcpm());
                mRewardAd.show();
            } else {
                LogProxy.w(TAG, "激励视频未就绪，请先 Load");
                refreshLogs();
            }
        });
    }

    private void loadRewardedVideo() {
        LogProxy.i(TAG, "========== 开始加载激励视频 ==========");
        LogProxy.i(TAG, "SlotId: " + getSlotIdKey() + " | Strategy: " + strategyLabel());

        mRewardAd = null;

        FsRewardedVideoAdManager.loadAd(this, new AdRequestParams.Builder()
                .slotId(getSlotIdKey())
                .defaultStrategyJson(mDefaultStrategyJson)
                .build(), new FsRewardedVideoAdManager.OnFsRewardedVideoAdLoadListener() {
            @Override
            public void onAdLoaded(IFsUnionRewardedVideoAd ad) {
                mRewardAd = ad;
                LogProxy.i(TAG, "激励视频加载成功: " + ad.getSdkName()
                        + " ecpm=" + ad.getEcpm());
                ad.setListener(new FsUnionRewardedVideoAdListener() {
                    @Override public void onAdShow(IFsUnionRewardedVideoAd ad) {
                        LogProxy.i(TAG, "激励视频展示: " + ad.getSdkName());
                        updateRewardStatus("状态: 展示中\n来源: " + ad.getSdkName());
                        refreshLogs();
                    }
                    @Override public void onAdClose(IFsUnionRewardedVideoAd ad) {
                        LogProxy.i(TAG, "激励视频关闭 (累计奖励: " + mRewardCount + "次)");
                        updateRewardStatus("状态: 已关闭\n累计获得奖励: " + mRewardCount + "次");
                        mRewardAd = null;
                        refreshLogs();
                    }
                    @Override public void onRewardVerify(IFsUnionRewardedVideoAd ad, boolean ok, int amt, String name) {
                        LogProxy.i(TAG, "奖励验证: success=" + ok + ", amount=" + amt + ", name=" + name);
                        String status = ok
                                ? String.format(Locale.getDefault(), "奖励验证通过!\n金额: %d | 名称: %s\n来源: %s", amt, name, ad.getSdkName())
                                : "奖励验证失败\n来源: " + ad.getSdkName();
                        updateRewardStatus(status);
                        if (ok) mRewardCount++;
                        refreshLogs();
                    }
                    @Override public void onAdClick(IFsUnionRewardedVideoAd ad) {
                        LogProxy.i(TAG, "激励视频点击: " + ad.getSdkName());
                        refreshLogs();
                    }
                    @Override public void onVideoComplete(IFsUnionRewardedVideoAd ad) {
                        LogProxy.i(TAG, "视频播放完成: " + ad.getSdkName());
                        refreshLogs();
                    }
                    @Override public void onAdError(IFsUnionRewardedVideoAd ad, int code, String msg) {
                        LogProxy.e(TAG, "激励视频错误 [" + code + "]: " + msg);
                        mRewardAd = null;
                        refreshLogs();
                    }
                });
                updateRewardStatus("状态: 已加载 → 可展示\n来源: " + ad.getSdkName()
                        + " | ecpm=" + ad.getEcpm());
                refreshLogs();
            }

            @Override
            public void onAdLoadError(int errorCode, String errorMsg) {
                LogProxy.e(TAG, "激励视频加载失败 [" + errorCode + "]: " + errorMsg);
                updateRewardStatus("加载失败 [" + errorCode + "]: " + errorMsg);
                refreshLogs();
            }
        });

        updateRewardStatus("加载中...");
        refreshLogs();
    }

    private void updateRewardStatus(String status) {
        if (mRewardStatus != null) mRewardStatus.setText(status);
    }
}
