package com.ima.union.adapters.fission;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.ima.union.utils.FsLogger;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.FeedAdAdapter;
import com.ima.union.core.adapter.FeedAdListener;
import com.ima.union.core.adapter.IFsNativeMaterialProvider;
import com.ima.union.core.model.AdAppMiitInfo;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdInteractionType;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.FsAdErrorCode;

import com.zm.fissionsdk.api.FissionSdk;
import com.zm.fissionsdk.api.FissionSlot;
import com.zm.fissionsdk.api.interfaces.IFissionLoadManager;
import com.zm.fissionsdk.api.interfaces.IFissionNative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞梭信息流自渲染广告适配器。
 *
 * <p>自渲染广告由宿主自行构建 UI，适配器提供素材提取（通过 {@link IFsNativeMaterialProvider} 接口）
 * 和点击交互注册（通过 {@link FeedAdAdapter#registerNativeAdInteraction}）。</p>
 */
public class FissionFeedRenderAdapter extends FissionBaseAdapter
        implements FeedAdAdapter, IFsNativeMaterialProvider {


    private static final String TAG = "FissionFeedRenderAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        FsLogger.d(TAG, "▶ request[FeedRender]: sdkName=" + sdkName + " slotId=" + sourceConfig.getAdUnitId()
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());
        logRequestedSizeIfPresent("request[FeedRender]", params);

        try {
            FissionSlot slot = buildSlot(context, sourceConfig, params.getVideoMuted());
            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            FissionSdk.getLoadManager().loadNative(slot, new IFissionLoadManager.AsyncNativeLoadListener() {
                @Override
                public void onLoad(List<IFissionNative> list) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    if (list != null && !list.isEmpty()) {
                        IFissionNative ad = list.get(0);
                        double ecpm = ad.getECpm();
                        FsLogger.d(TAG, "request[FeedRender]: ecpm=" + ecpm);
                        Map<String, Object> extra = new HashMap<>();
                        extra.put("is_template", false);
                        UnionAdResponse response = new UnionAdResponse.Builder()
                                .sdkName(sourceConfig.getSdkName())
                                .adUnitId(sourceConfig.getAdUnitId())
                                .sdkType(com.ima.union.core.model.AdSdkType.FISSION)
                                .adFormat(AdFormat.FEED_RENDER)
                                .ecpm(ecpm)
                                .expireTimeMs(System.currentTimeMillis() + sourceConfig.getExpireTimeMs())
                                .nativeAd(ad)
                                .extra(extra)
                                .build();
                        if (callback != null)
                            callback.onLoaded(response);
                    } else {
                        FsLogger.w(TAG, "request[FeedRender]: no fill");
                        if (callback != null)
                            callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sdkName, 0, ""));
                    }
                }

                @Override
                public void onError(int code, String msg) {
                    if (timeoutCtrl.isCompleted()) return;
                    timeoutCtrl.finish();
                    FsLogger.e(TAG, "request[FeedRender]: code=" + code + " msg=" + msg);
                    if (callback != null)
                        callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, code, msg));
                }
            });
        } catch (Exception e) {
            FsLogger.e(TAG, "request[FeedRender] exception: " + e.getMessage(), e);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
        }
    }

    @Override
    public View renderFeedAd(Context context, UnionAdResponse response, FeedAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "renderFeedAd: response is null");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        FsLogger.d(TAG, "▶ renderFeedAd[Render]: sourceId=" + response.getSdkName());
        if (!(response.getNativeAd() instanceof IFissionNative)) {
            FsLogger.e(TAG, "renderFeedAd[Render]: nativeAd is not IFissionNative");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        // 自渲染：素材由 IFsNativeMaterialProvider 提取，宿主自行构建 View
        if (listener != null)
            listener.onFeedAdRendered();
        FsLogger.d(TAG, "renderFeedAd[Render]: self-render mode, caller builds the view");
        return null;
    }

    // ════════════════════════════════════════════════════════════════
    //  FeedAdAdapter#registerNativeAdInteraction — 点击交互注册
    // ════════════════════════════════════════════════════════════════
    @Override
    public void registerNativeAdInteraction(UnionAdResponse response,
                                            ViewGroup containerView,
                                            List<View> clickableViews,
                                            FeedAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: response is null");
            return;
        }
        IFissionNative ad = (IFissionNative) response.getNativeAd();
        if (ad == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: nativeAd is null");
            return;
        }
        if (containerView == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: containerView is null");
            return;
        }
        try {
            List<View> clickViews = clickableViews != null ? new ArrayList<>(clickableViews) : new ArrayList<>();
            // setNativeInteractionListener(container, clickViews, creativeViews, shakeViews, closeView, listener)
            ad.setNativeInteractionListener(containerView, clickViews, null, null, null,
                    new IFissionNative.NativeInteractionListener() {
                        @Override
                        public void onShow() {
                            FsLogger.d(TAG, "FissionNative.onShow: sourceId=" + response.getSdkName());
                            if (listener != null) listener.onAdShow();
                        }

                        @Override
                        public void onClick(View view) {
                            FsLogger.d(TAG, "FissionNative.onClick: sourceId=" + response.getSdkName());
                            if (listener != null) listener.onAdClick();
                        }

                        @Override
                        public void onShowFailed(int code, String msg) {
                            FsLogger.w(TAG, "FissionNative.onShowFailed: code=" + code + " msg=" + msg
                                    + " sourceId=" + response.getSdkName());
                            if (listener != null)
                                listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), code, msg));
                        }

                        @Override
                        public void onCreativeClick(View view) {
                            FsLogger.d(TAG, "FissionNative.onCreativeClick: sourceId=" + response.getSdkName());
                        }
                    });
            FsLogger.d(TAG, "registerNativeAdInteraction: registered " + clickViews.size() + " clickable views");
        } catch (Exception e) {
            FsLogger.w(TAG, "registerNativeAdInteraction: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  IFsNativeMaterialProvider — 从 IFissionNative 提取素材
    // ════════════════════════════════════════════════════════════════

    @Override
    public String getTitle(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return null;
        try {
            return ((IFissionNative) nativeAd).getTitle();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getDescription(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return null;
        try {
            return ((IFissionNative) nativeAd).getDesc();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getIconUrl(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return null;
        try {
            return ((IFissionNative) nativeAd).getAppIcon();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getImageUrl(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return null;
        try {
            List<String> list = ((IFissionNative) nativeAd).getImageList();
            return (list != null && !list.isEmpty()) ? list.get(0) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> getImageList(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return Collections.emptyList();
        try {
            List<String> list = ((IFissionNative) nativeAd).getImageList();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public String getCallToAction(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return null;
        try {
            return ((IFissionNative) nativeAd).getBtnText();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getImageWidth(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return 0;
        try {
            return ((IFissionNative) nativeAd).getMaterialWidth();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getImageHeight(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return 0;
        try {
            return ((IFissionNative) nativeAd).getMaterialHeight();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public AdInteractionType getInteractionType(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return AdInteractionType.UNKNOWN;
        int rawType = ((IFissionNative) nativeAd).getInteractionType();
        // Fission: DEEPLINK(1)→DEEP_LINK, MINI_PROGRAM(2)→LANDING_PAGE,
        //          LANDING_PAGE(3)→LANDING_PAGE, DOWNLOAD(4)→DOWNLOAD
        if (rawType == 1) return AdInteractionType.DEEP_LINK;
        if (rawType == 2 || rawType == 3) return AdInteractionType.LANDING_PAGE;
        if (rawType == 4) return AdInteractionType.DOWNLOAD;
        return AdInteractionType.UNKNOWN;
    }

    @Override
    public long getAppSize(Object nativeAd) {
        return 0L; // Fission 不直接提供 App Size
    }

    @Override
    public AdAppMiitInfo getAppMiitInfo(Object nativeAd) {
        if (!(nativeAd instanceof IFissionNative)) return null;
        try {
            IFissionNative ad = (IFissionNative) nativeAd;
            return new AdAppMiitInfo.Builder()
                    .appName(ad.getAppName())
                    .developerName(ad.getDeveloperName())
                    .appVersion(ad.getAppVersion())
                    .privacyUrl(ad.getPrivacyUrl())
                    .permissionUrl(ad.getPermissionUrl())
                    .functionDescUrl(ad.getFunctionDescUrl())
                    .build();
        } catch (Exception e) {
            FsLogger.w(TAG, "getAppMiitInfo: " + e.getMessage());
            return null;
        }
    }
}
