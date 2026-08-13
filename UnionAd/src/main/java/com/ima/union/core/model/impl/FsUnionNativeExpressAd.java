package com.ima.union.core.model.impl;


import com.ima.union.utils.FsLogger;
import android.content.Context;
import android.view.View;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.FeedAdAdapter;
import com.ima.union.core.adapter.FeedAdListener;
import com.ima.union.core.model.entry.IFsUnionNativeExpressAd;
import com.ima.union.core.model.listener.FsUnionNativeExpressAdListener;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;

/**
 * 信息流模板广告对象实现 — 内部 SDK 使用，外部请通过 {@link IFsUnionNativeExpressAd} 接口访问。
 * <p>封装了已加载的模板广告，提供模板 View 获取和展示回调。</p>
 */
public class FsUnionNativeExpressAd implements IFsUnionNativeExpressAd {

        private final Context context;
    private final UnionAdResponse response;
    private final AdAdapter adapter;
    private final String slotId;
    private FsUnionNativeExpressAdListener listener;
    private View expressView;
    private boolean viewRendered = false;

    /** 构造器（保留 public 以兼容 SDK 内部使用，外部业务方应使用各格式 Manager 加载回调返回的接口对象） */
    public FsUnionNativeExpressAd(Context context, String slotId, UnionAdResponse response, AdAdapter adapter) {
        this.context = context;
        this.slotId = slotId;
        this.response = response;
        this.adapter = adapter;
    }

    // ========== 广告信息 ==========

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
    public void setListener(FsUnionNativeExpressAdListener listener) {
        this.listener = listener;
    }

    // ========== 模板视图 ==========

    @Override
    public View getExpressView() {
        if (expressView != null) {
            return expressView;
        }
        if (!(adapter instanceof FeedAdAdapter)) {
            FsLogger.e("FsUnionNativeExpressAd", "Adapter " + adapter.getClass().getSimpleName() + " does not support feed template");
            if (listener != null) {
                listener.onAdError(this, FsAdErrorCode.ADAPTER_TYPE_MISMATCH, FsAdErrorCode.buildMsg("适配器类型不匹配"));
            }
            return null;
        }
        expressView = ((FeedAdAdapter) adapter).renderFeedAd(context, response,
                new FeedAdListener() {
                    @Override
                    public void onFeedAdRendered() {
                        viewRendered = true;
                        if (listener != null) {
                            listener.onExpressAdRendered(FsUnionNativeExpressAd.this);
                        }
                    }

                    @Override
                    public void onAdClose() {
                        if (listener != null) {
                            listener.onAdClose(FsUnionNativeExpressAd.this);
                        }
                    }

                    @Override
                    public void onAdShow() {
                        if (listener != null) {
                            listener.onAdShow(FsUnionNativeExpressAd.this);
                        }
                    }

                    @Override
                    public void onAdClick() {
                        if (listener != null) {
                            listener.onAdClick(FsUnionNativeExpressAd.this);
                        }
                    }

                    @Override
                    public void onAdError(int errorCode, String errorMsg) {
                        if (listener != null) {
                            listener.onAdError(FsUnionNativeExpressAd.this, errorCode, errorMsg);
                        }
                    }
                });
        return expressView;
    }

    @Override
    public boolean isViewRendered() {
        return viewRendered;
    }

    @Override
    public void destroy() {
        if (listener != null) {
            listener.onAdClose(FsUnionNativeExpressAd.this);
        }
        expressView = null;
        listener = null;
    }
}
