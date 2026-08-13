package com.ima.union.core.model.impl;

import android.view.View;
import android.view.ViewGroup;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.FeedAdAdapter;
import com.ima.union.core.adapter.FeedAdListener;
import com.ima.union.core.adapter.IFsNativeMaterialProvider;
import com.ima.union.core.model.AdAppMiitInfo;
import com.ima.union.core.model.AdInteractionType;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.entry.IFsUnionNativeAd;
import com.ima.union.core.model.listener.FsUnionNativeAdListener;
import com.ima.union.utils.FsLogger;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 信息流自渲染广告对象实现 — 内部 SDK 使用，外部请通过 {@link IFsUnionNativeAd} 接口访问。
 *
 * <p>素材获取：适配器实现 {@link IFsNativeMaterialProvider}，直接从 SDK 原生对象提取素材。</p>
 *
 * <p>点击交互注册：通过 {@link FeedAdAdapter#registerNativeAdInteraction} 委托给平台适配器，
 * 各适配器调用对应 SDK 的点击注册 API（如 Pangle 的 registerViewForInteraction）。</p>
 */
public class FsUnionNativeAd implements IFsUnionNativeAd {

    private static final String TAG = "FsUnionNativeAd";

    private final UnionAdResponse response;
    private final AdAdapter adapter;
    private final String slotId;

    /** 素材提取器（优先；若适配器未实现则为 null） */
    private final IFsNativeMaterialProvider materialProvider;

    /** 构造器（保留 public 以兼容 SDK 内部使用，外部业务方应使用各格式 Manager 加载回调返回的接口对象） */
    public FsUnionNativeAd(String slotId, UnionAdResponse response, AdAdapter adapter) {
        this.response = response;
        this.adapter = adapter;
        this.slotId = slotId;
        this.materialProvider = adapter instanceof IFsNativeMaterialProvider
                ? (IFsNativeMaterialProvider) adapter : null;
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

    // ========== 物料素材 ==========

    @Override
    public String getTitle() {
        return materialProvider != null ? materialProvider.getTitle(response.getNativeAd()) : null;
    }

    @Override
    public String getDescription() {
        return materialProvider != null ? materialProvider.getDescription(response.getNativeAd()) : null;
    }

    @Override
    public String getIconUrl() {
        return materialProvider != null ? materialProvider.getIconUrl(response.getNativeAd()) : null;
    }

    @Override
    public String getImageUrl() {
        return materialProvider != null ? materialProvider.getImageUrl(response.getNativeAd()) : null;
    }

    @Override
    public List<String> getImageList() {
        return materialProvider != null
                ? materialProvider.getImageList(response.getNativeAd())
                : Collections.emptyList();
    }

    @Override
    public int getImageWidth() {
        return materialProvider != null ? materialProvider.getImageWidth(response.getNativeAd()) : 0;
    }

    @Override
    public int getImageHeight() {
        return materialProvider != null ? materialProvider.getImageHeight(response.getNativeAd()) : 0;
    }

    @Override
    public String getCallToAction() {
        return materialProvider != null ? materialProvider.getCallToAction(response.getNativeAd()) : null;
    }

    @Override
    public double getRating() {
        return materialProvider != null ? materialProvider.getRating(response.getNativeAd()) : 0.0;
    }

    @Override
    public String getVideoUrl() {
        return materialProvider != null ? materialProvider.getVideoUrl(response.getNativeAd()) : null;
    }

    @Override
    public Object getNativeAd() {
        return response.getNativeAd();
    }

    @Override
    public Map<String, Object> getExtra() {
        return response.getExtra();
    }

    // ========== 广告能力信息 ==========

    @Override
    public AdInteractionType getInteractionType() {
        return materialProvider != null
                ? materialProvider.getInteractionType(response.getNativeAd())
                : AdInteractionType.UNKNOWN;
    }

    @Override
    public boolean isDownloadAd() {
        return materialProvider != null && materialProvider.isDownloadAd(response.getNativeAd());
    }

    @Override
    public long getAppSize() {
        return materialProvider != null ? materialProvider.getAppSize(response.getNativeAd()) : 0L;
    }

    // ========== 合规信息 ==========

    @Override
    public AdAppMiitInfo getAppMiitInfo() {
        return materialProvider != null ? materialProvider.getAppMiitInfo(response.getNativeAd()) : null;
    }

    // ========== 点击交互注册 ==========

    @Override
    public void registerViewForInteraction(ViewGroup containerView, List<View> clickableViews, FsUnionNativeAdListener listener) {
        Object nativeAd = response.getNativeAd();
        if (nativeAd == null) {
            FsLogger.e(TAG, "registerViewForInteraction: nativeAd is null");
            return;
        }
        if (!(adapter instanceof FeedAdAdapter)) {
            FsLogger.w(TAG, "registerViewForInteraction: adapter does not support FeedAdAdapter, cannot register click interactions");
            return;
        }

        FeedAdAdapter feedAdapter = (FeedAdAdapter) adapter;
        FeedAdListener feedListener = buildFeedListener(listener);
        feedAdapter.registerNativeAdInteraction(response, containerView, clickableViews, feedListener);
        FsLogger.d(TAG, "registerViewForInteraction: registered via " + adapter.getClass().getSimpleName());
    }

    /**
     * 构建 FeedAdListener，将 SDK 回调桥接到 FsUnionNativeAdListener。
     */
    private FeedAdListener buildFeedListener(FsUnionNativeAdListener listener) {
        return new FeedAdListener() {
            @Override
            public void onFeedAdRendered() { /* SDK 回调，自渲染无需处理 */ }

            @Override
            public void onAdShow() {
                FsLogger.d(TAG, "onAdShow");
                if (listener != null) listener.onAdShow(FsUnionNativeAd.this);
            }

            @Override
            public void onAdClick() {
                FsLogger.d(TAG, "onAdClick");
                if (listener != null) listener.onAdClick(FsUnionNativeAd.this);
            }

            @Override
            public void onAdError(int errorCode, String errorMsg) {
                FsLogger.e(TAG, "onAdError: code=" + errorCode + " msg=" + errorMsg);
                if (listener != null) listener.onAdError(FsUnionNativeAd.this, errorCode, errorMsg);
            }

            @Override
            public void onFeedAdDislike() {
                FsLogger.d(TAG, "onFeedAdDislike");
            }
        };
    }

    // ========== 上报接口 ==========

    @Override
    public void reportShow() {
        // 各平台 SDK 的曝光上报已在 registerNativeAdInteraction 中由适配器注册
    }

    @Override
    public void reportClick() {
        // 各平台 SDK 的点击上报已在 registerNativeAdInteraction 中由适配器注册
    }

    @Override
    public void destroy() {
    }
}
