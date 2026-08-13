package com.ima.union.adapters.baidu;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.baidu.mobads.sdk.api.BaiduNativeManager;
import com.baidu.mobads.sdk.api.NativeResponse;
import com.baidu.mobads.sdk.api.RequestParameters;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.FeedAdAdapter;
import com.ima.union.core.adapter.FeedAdListener;
import com.ima.union.core.adapter.IFsNativeMaterialProvider;
import com.ima.union.core.model.AdAppMiitInfo;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdInteractionType;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.BidLossReason;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.utils.FsLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 百青藤信息流自渲染广告适配器。
 *
 * <p>通过 {@link BaiduNativeManager#loadFeedAd} 加载自渲染广告，
 * 广告物料通过 {@link IFsNativeMaterialProvider} 接口提供。
 * 宿主自行构建 UI 后调用 {@link #registerNativeAdInteraction} 注册曝光/点击计费，
 * 并使用 {@link NativeResponse#registerViewForInteraction} 完成交互绑定。</p>
 *
 * <p><b>注意</b>：百青藤自渲染广告需要手动发送曝光和点击事件，否则影响计费。
 * 必须调用 registerViewForInteraction，否则曝光/点击回调不会触发。</p>
 */
public class BaiduFeedRenderAdapter extends BaiduBaseAdapter implements FeedAdAdapter, IFsNativeMaterialProvider {

    private static final String TAG = "BaiduFeedRenderAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String adPlaceId = sourceConfig.getAdUnitId();
        FsLogger.d(TAG, "▶ request[FeedRender]: sdkName=" + sdkName + " adPlaceId=" + adPlaceId + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());

        try {
            BaiduNativeManager manager = new BaiduNativeManager(context, adPlaceId);
            if (sourceConfig.getBidFloor() > 0) {
                manager.setBidFloor((int) sourceConfig.getBidFloor());
            }
            RequestParameters requestParams = new RequestParameters.Builder().build();
            manager.loadFeedAd(requestParams, new BaiduFeedRenderLoadCallback(sourceConfig, callback));
        } catch (Exception e) {
            FsLogger.e(TAG, "request[FeedRender] exception: " + e.getMessage(), e);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
        }
    }

    /**
     * 自渲染广告：素材由 {@link IFsNativeMaterialProvider} 提取，宿主自行构建 View，此处不返回 View。
     * 回调 {@link FeedAdListener#onFeedAdRendered} 通知宿主可以读取素材并渲染。
     */
    @Override
    public View renderFeedAd(Context context, UnionAdResponse response, FeedAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "renderFeedAd: response is null");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        FsLogger.d(TAG, "▶ renderFeedAd[Render]: sourceId=" + response.getSdkName());
        NativeResponse nativeResponse = (NativeResponse) response.getNativeAd();
        if (nativeResponse == null) {
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        if (!nativeResponse.isReady(context)) {
            FsLogger.w(TAG, "renderFeedAd[Render] ad expired (isReady=false), sourceId=" + response.getSdkName());
        }
        // 自渲染：素材已可通过 IFsNativeMaterialProvider 读取，通知宿主
        if (listener != null) listener.onFeedAdRendered();
        return null;
    }

    /**
     * 注册点击交互区域。
     *
     * <p>宿主构建完广告 View 并添加到容器后调用此方法。
     * 内部调用 {@link NativeResponse#registerViewForInteraction} 完成曝光/点击计费绑定。
     * clickViews 为可点击 View 列表，creativeViews 传 null（不触发下载整改弹框）。</p>
     */
    @Override
    public void registerNativeAdInteraction(UnionAdResponse response, ViewGroup containerView, List<View> clickableViews, FeedAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: response is null");
            return;
        }
        NativeResponse nativeResponse = (NativeResponse) response.getNativeAd();
        if (nativeResponse == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: NativeResponse is null");
            return;
        }
        if (containerView == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: containerView is null");
            return;
        }
        try {
            // registerViewForInteraction：
            //   view:          广告容器 View（用于曝光计费，必传）
            //   clickViews:    可点击 View 列表（触发点击跳转和计费）
            //   creativeViews: 带下载引导文案的 View（不触发下载整改弹框），传 null 即可
            //   listener:      曝光/点击回调
            List<View> clicks = clickableViews != null ? new ArrayList<>(clickableViews) : Collections.<View>singletonList(containerView);

            nativeResponse.registerViewForInteraction(containerView, clicks, null, new NativeResponse.AdInteractionListener() {
                @Override
                public void onAdClick() {
                    FsLogger.d(TAG, "BaiduNative.onAdClick: sourceId=" + response.getSdkName());
                    if (listener != null) listener.onAdClick();
                }

                @Override
                public void onADExposed() {
                    FsLogger.d(TAG, "BaiduNative.onADExposed: sourceId=" + response.getSdkName());
                    if (listener != null)
                        listener.onAdShow();
                }

                @Override
                public void onADExposureFailed(int reason) {
                    FsLogger.w(TAG, "BaiduNative.onADExposureFailed: reason=" + reason + " sourceId=" + response.getSdkName());
                    if (listener != null)
                        listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), 0, ""));
                }

                @Override
                public void onADStatusChanged() {
                }

                @Override
                public void onAdUnionClick() {
                }
            });

            FsLogger.d(TAG, "registerNativeAdInteraction: registered " + clicks.size() + " clickable views, sourceId=" + response.getSdkName());
        } catch (Exception e) {
            FsLogger.w(TAG, "registerNativeAdInteraction: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  IFsNativeMaterialProvider — 从 NativeResponse 提取广告素材
    // ════════════════════════════════════════════════════════════════

    @Override
    public String getTitle(Object nativeAd) {
        return nativeAd instanceof NativeResponse ? ((NativeResponse) nativeAd).getTitle() : null;
    }

    @Override
    public String getDescription(Object nativeAd) {
        return nativeAd instanceof NativeResponse ? ((NativeResponse) nativeAd).getDesc() : null;
    }

    @Override
    public String getIconUrl(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return null;
        try {
            return ((NativeResponse) nativeAd).getIconUrl();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getImageUrl(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return null;
        try {
            return ((NativeResponse) nativeAd).getImageUrl();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> getImageList(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return Collections.emptyList();
        try {
            String url = ((NativeResponse) nativeAd).getImageUrl();
            return url != null ? Collections.singletonList(url) : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public String getCallToAction(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return null;
        try {
            return ((NativeResponse) nativeAd).getActButtonString();
        } catch (Exception e) {
            return null;
        }
    }

    // Baidu NativeResponse 不提供评分接口，使用 IFsNativeMaterialProvider 默认值 0.0

    @Override
    public String getVideoUrl(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return null;
        try {
            return ((NativeResponse) nativeAd).getVideoUrl();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getImageWidth(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return 0;
        try {
            return ((NativeResponse) nativeAd).getMainPicWidth();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getImageHeight(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return 0;
        try {
            return ((NativeResponse) nativeAd).getMainPicHeight();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public AdInteractionType getInteractionType(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return AdInteractionType.UNKNOWN;
        try {
            int rawType = ((NativeResponse) nativeAd).getAdActionType();
            // Baidu: LANDING_PAGE(1)→LANDING_PAGE, APP_DOWNLOAD(2)→DOWNLOAD, DEEP_LINK(3)→DEEP_LINK
            if (rawType == 1) return AdInteractionType.LANDING_PAGE;
            if (rawType == 2) return AdInteractionType.DOWNLOAD;
            if (rawType == 3) return AdInteractionType.DEEP_LINK;
            return AdInteractionType.UNKNOWN;
        } catch (Exception e) {
            return AdInteractionType.UNKNOWN;
        }
    }

    @Override
    public long getAppSize(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return 0L;
        try {
            return ((NativeResponse) nativeAd).getAppSize();
        } catch (Exception e) {
            return 0L;
        }
    }

    @Override
    public AdAppMiitInfo getAppMiitInfo(Object nativeAd) {
        if (!(nativeAd instanceof NativeResponse)) return null;
        try {
            NativeResponse ad = (NativeResponse) nativeAd;
            return new AdAppMiitInfo.Builder().appName(ad.getTitle()).developerName(ad.getPublisher()).appVersion(ad.getAppVersion()).privacyUrl(ad.getAppPrivacyLink()).permissionUrl(ad.getAppPermissionLink()).functionDescUrl(ad.getAppFunctionLink()).build();
        } catch (Exception e) {
            FsLogger.w(TAG, "getAppMiitInfo: " + e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  加载回调内部类
    // ════════════════════════════════════════════════════════════════

    private class BaiduFeedRenderLoadCallback implements BaiduNativeManager.FeedAdListener {
        private final AdSourceConfig sourceConfig;
        private final AdCallback callback;
        private NativeResponse lastNativeResponse;

        BaiduFeedRenderLoadCallback(AdSourceConfig sourceConfig, AdCallback callback) {
            this.sourceConfig = sourceConfig;
            this.callback = callback;
        }

        @Override
        public void onNativeLoad(List<NativeResponse> nativeResponses) {
            if (nativeResponses != null && !nativeResponses.isEmpty()) {
                lastNativeResponse = nativeResponses.get(0);
                double ecpm = getEcpmFromAd(lastNativeResponse);
                FsLogger.d(TAG, "request[FeedRender]: onNativeLoad, sourceId=" + sourceConfig.getSdkName() + " ecpm=" + ecpm);
                Map<String, Object> extra = new HashMap<>();
                extra.put("is_bidding", true);
                if (callback != null)
                    callback.onLoaded(buildResponse(sourceConfig, AdFormat.FEED_RENDER, lastNativeResponse, ecpm, extra, readAdIsReady(lastNativeResponse)));
            } else {
                FsLogger.w(TAG, "request[FeedRender]: onNativeLoad empty list");
                if (callback != null)
                    callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sourceConfig.getSdkName(), 0, ""));
            }
        }

        @Override
        public void onNativeFail(int errorCode, String message, NativeResponse nativeResponse) {
            FsLogger.e(TAG, "request[FeedRender]: onNativeFail, sourceId=" + sourceConfig.getSdkName() + " code=" + errorCode + " msg=" + message);
            if (nativeResponse != null) reportBidFail(nativeResponse, BidLossReason.LOAD_FAILED);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sourceConfig.getSdkName(), errorCode, message));
        }

        @Override
        public void onNoAd(int code, String msg, NativeResponse nativeResponse) {
            FsLogger.w(TAG, "request[FeedRender]: onNoAd, sourceId=" + sourceConfig.getSdkName() + " code=" + code + " msg=" + msg);
            if (nativeResponse != null) reportBidFail(nativeResponse, BidLossReason.NO_AD);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sourceConfig.getSdkName(), code, msg));
        }

        @Override
        public void onVideoDownloadSuccess() {
        }

        @Override
        public void onVideoDownloadFailed() {
        }

        @Override
        public void onLpClosed() {
        }
    }
}
