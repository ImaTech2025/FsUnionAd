package com.ima.union.core.model.impl;

import android.content.Context;
import android.view.ViewGroup;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.SplashAdAdapter;
import com.ima.union.core.adapter.SplashAdListener;
import com.ima.union.core.model.entry.IFsUnionSplashAd;
import com.ima.union.core.model.listener.FsUnionSplashAdListener;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;

/**
 * 开屏广告对象实现 — 内部 SDK 使用，外部请通过 {@link IFsUnionSplashAd} 接口访问。
 */
public class FsUnionSplashAd implements IFsUnionSplashAd {

    private final Context context;
    private final UnionAdResponse response;
    private final AdAdapter adapter;
    private final String slotId;
    private FsUnionSplashAdListener listener;

    /** 构造器（保留 public 以兼容 SDK 内部使用，外部业务方应使用各格式 Manager 加载回调返回的接口对象） */
    public FsUnionSplashAd(Context context, String slotId, UnionAdResponse response, AdAdapter adapter) {
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
    public void setListener(FsUnionSplashAdListener listener) {
        this.listener = listener;
    }

    @Override
    public void show(ViewGroup container) {
        if (!(adapter instanceof SplashAdAdapter)) {
            if (listener != null) {
                listener.onAdError(this, FsAdErrorCode.ADAPTER_TYPE_MISMATCH, FsAdErrorCode.buildMsg("适配器类型不匹配"));
            }
            return;
        }
        ((SplashAdAdapter) adapter).showSplash(context, response, container, new SplashAdListener() {
            @Override
            public void onSplashAdSkipped() {
                if (listener != null) {
                    listener.onSplashAdSkipped(FsUnionSplashAd.this);
                }
            }

            @Override
            public void onAdShow() {
                if (listener != null) {
                    listener.onAdShow(FsUnionSplashAd.this);
                }
            }

            @Override
            public void onAdClick() {
                if (listener != null) {
                    listener.onAdClick(FsUnionSplashAd.this);
                }
            }

            @Override
            public void onAdClose() {
                if (listener != null) {
                    listener.onAdClose(FsUnionSplashAd.this);
                }
            }

            @Override
            public void onAdError(int errorCode, String errorMsg) {
                if (listener != null) {
                    listener.onAdError(FsUnionSplashAd.this, errorCode, errorMsg);
                }
            }
        });
    }
}
