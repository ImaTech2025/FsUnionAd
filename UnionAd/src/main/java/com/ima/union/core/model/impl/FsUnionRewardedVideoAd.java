package com.ima.union.core.model.impl;

import android.content.Context;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.RewardedVideoAdAdapter;
import com.ima.union.core.adapter.RewardedVideoAdListener;
import com.ima.union.core.model.entry.IFsUnionRewardedVideoAd;
import com.ima.union.core.model.listener.FsUnionRewardedVideoAdListener;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;

/**
 * 激励视频广告对象实现 — 内部 SDK 使用，外部请通过 {@link IFsUnionRewardedVideoAd} 接口访问。
 */
public class FsUnionRewardedVideoAd implements IFsUnionRewardedVideoAd {

    private final Context context;
    private final UnionAdResponse response;
    private final AdAdapter adapter;
    private final String slotId;
    private FsUnionRewardedVideoAdListener listener;

    /** 构造器（保留 public 以兼容 SDK 内部使用，外部业务方应使用各格式 Manager 加载回调返回的接口对象） */
    public FsUnionRewardedVideoAd(Context context, String slotId, UnionAdResponse response, AdAdapter adapter) {
        this.context = context;
        this.slotId = slotId;
        this.response = response;
        this.adapter = adapter;
    }

    @Override
    public double getEcpm() {
        return response.getEcpm();
    }

    @Override
    public AdSdkType getSdkType() {
        return response.getSdkType();
    }

    @Override
    public String getSdkName() {
        return response.getSdkName();
    }

    @Override
    public String getSlotId() {
        return slotId;
    }

    @Override
    public String getAdUnitId() {
        return response.getAdUnitId();
    }

    @Override
    public boolean isReady() {
        return response.isReady();
    }

    @Override
    public void setListener(FsUnionRewardedVideoAdListener listener) {
        this.listener = listener;
    }

    @Override
    public void show() {
        if (!(adapter instanceof RewardedVideoAdAdapter)) {
            if (listener != null) {
                listener.onAdError(this, FsAdErrorCode.ADAPTER_TYPE_MISMATCH, FsAdErrorCode.buildMsg("适配器类型不匹配"));
            }
            return;
        }
        ((RewardedVideoAdAdapter) adapter).showRewardedVideo(context, response,
                new RewardedVideoAdListener() {
                    @Override
                    public void onRewardVerify(boolean rewardVerify,
                                               int rewardAmount, String rewardName) {
                        if (listener != null) {
                            listener.onRewardVerify(FsUnionRewardedVideoAd.this,
                                    rewardVerify, rewardAmount, rewardName);
                        }
                    }

                    @Override
                    public void onAdShow() {
                        if (listener != null) {
                            listener.onAdShow(FsUnionRewardedVideoAd.this);
                        }
                    }

                    @Override
                    public void onAdClick() {
                        if (listener != null) {
                            listener.onAdClick(FsUnionRewardedVideoAd.this);
                        }
                    }

                    @Override
                    public void onAdClose() {
                        if (listener != null) {
                            listener.onAdClose(FsUnionRewardedVideoAd.this);
                        }
                    }

                    @Override
                    public void onVideoComplete() {
                        if (listener != null) {
                            listener.onVideoComplete(FsUnionRewardedVideoAd.this);
                        }
                    }

                    @Override
                    public void onAdError(int errorCode, String errorMsg) {
                        if (listener != null) {
                            listener.onAdError(FsUnionRewardedVideoAd.this, errorCode, errorMsg);
                        }
                    }
                });
    }
}
