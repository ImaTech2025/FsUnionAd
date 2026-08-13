package com.ima.union.core.model.impl;

import android.content.Context;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.InterstitialAdAdapter;
import com.ima.union.core.adapter.InterstitialAdListener;
import com.ima.union.core.model.entry.IFsUnionInterstitialAd;
import com.ima.union.core.model.listener.FsUnionInterstitialAdListener;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;

/**
 * 插屏广告对象实现 — 内部 SDK 使用，外部请通过 {@link IFsUnionInterstitialAd} 接口访问。
 */
public class FsUnionInterstitialAd implements IFsUnionInterstitialAd {

    private final Context context;
    private final UnionAdResponse response;
    private final AdAdapter adapter;
    private final String slotId;
    private FsUnionInterstitialAdListener listener;

    /** 构造器（保留 public 以兼容 SDK 内部使用，外部业务方应使用各格式 Manager 加载回调返回的接口对象） */
    public FsUnionInterstitialAd(Context context, String slotId, UnionAdResponse response, AdAdapter adapter) {
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
    public void setListener(FsUnionInterstitialAdListener listener) {
        this.listener = listener;
    }

    @Override
    public void show() {
        if (!(adapter instanceof InterstitialAdAdapter)) {
            if (listener != null) {
                listener.onAdError(this, FsAdErrorCode.ADAPTER_TYPE_MISMATCH, FsAdErrorCode.buildMsg("适配器类型不匹配"));
            }
            return;
        }
        ((InterstitialAdAdapter) adapter).showInterstitial(context, response,
                new InterstitialAdListener() {
                    @Override
                    public void onAdShow() {
                        if (listener != null) {
                            listener.onAdShow(FsUnionInterstitialAd.this);
                        }
                    }

                    @Override
                    public void onAdClick() {
                        if (listener != null) {
                            listener.onAdClick(FsUnionInterstitialAd.this);
                        }
                    }

                    @Override
                    public void onAdClose() {
                        if (listener != null) {
                            listener.onAdClose(FsUnionInterstitialAd.this);
                        }
                    }

                    @Override
                    public void onAdError(int errorCode, String errorMsg) {
                        if (listener != null) {
                            listener.onAdError(FsUnionInterstitialAd.this, errorCode, errorMsg);
                        }
                    }
                });
    }
}
