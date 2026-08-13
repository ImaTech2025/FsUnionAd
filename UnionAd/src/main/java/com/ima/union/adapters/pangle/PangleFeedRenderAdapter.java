package com.ima.union.adapters.pangle;

import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.ComplianceInfo;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTFeedAd;
import com.bytedance.sdk.openadsdk.TTImage;
import com.bytedance.sdk.openadsdk.TTNativeAd;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 穿山甲信息流自渲染广告适配器。
 *
 * <p>自渲染广告由宿主自行构建 UI，适配器提供素材提取（通过 {@link IFsNativeMaterialProvider} 接口）
 * 和点击交互注册（通过 {@link FeedAdAdapter#registerNativeAdInteraction}）。</p>
 */
public class PangleFeedRenderAdapter extends PangleBaseAdapter
        implements FeedAdAdapter, IFsNativeMaterialProvider {


    private static final String TAG = "PangleFeedRenderAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String codeId = resolveCodeId(sourceConfig);
        FsLogger.d(TAG, "▶ request[FeedRender]: sdkName=" + sdkName + " codeId=" + codeId
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());


        try {
            // 信息流自渲染默认图片尺寸 640x320（Pangle 建议值）
            int imgW = 640, imgH = 320;
            if (params != null) {
                int[] imageSize = params.getImageAcceptedSize();
                if (imageSize != null) {
                    imgW = imageSize[0];
                    imgH = imageSize[1];
                }
            }
            AdSlot adSlot = buildAdSlot(codeId, AdFormat.FEED_RENDER, imgW, imgH, params);
            TTAdNative ttAdNative = createTtAdNative(context);
            if (ttAdNative == null) {
                if (callback != null)
                    callback.onLoadFailed(FsAdErrorCode.ADAPTER_CREATE_FAILED, FsAdErrorCode.buildMsg("SDK客户端创建失败", sdkName, 0, ""));
                return;
            }

            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            ttAdNative.loadFeedAd(adSlot, new TTAdNative.FeedAdListener() {
                @Override
                public void onError(int code, String msg) {
                    timeoutCtrl.finish();
                    if (callback != null)
                        callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, code, msg));
                }

                @Override
                public void onFeedAdLoad(List<TTFeedAd> ads) {
                    timeoutCtrl.finish();
                    if (ads != null && !ads.isEmpty()) {
                        TTFeedAd ad = ads.get(0);
                        double ecpm = getEcpmFromAd(ad);
                        FsLogger.d(TAG, "request[FeedRender]: ecpm=" + ecpm);
                        // 自渲染素材通过 IFsNativeMaterialProvider 直接提取，不再塞 extra Map
                        UnionAdResponse response = buildResponse(sourceConfig, AdFormat.FEED_RENDER, ad, ecpm);
                        if (callback != null)
                            callback.onLoaded(response);
                    } else {
                        if (callback != null)
                            callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sdkName, 0, ""));
                    }
                }
            });
        } catch (Exception e) {
            FsLogger.e(TAG, "request[FeedRender] exception: " + e.getMessage(), e);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  FeedAdAdapter — 渲染（模板/自渲染共用，返回 getAdView）
    // ════════════════════════════════════════════════════════════════

    @Override
    public View renderFeedAd(Context context, UnionAdResponse response, FeedAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "renderFeedAd: response is null");
            if (listener != null) listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        FsLogger.d(TAG, "▶ renderFeedAd[Render]: sourceId=" + response.getSdkName());
        TTFeedAd ad = (TTFeedAd) response.getNativeAd();
        if (ad == null) {
            FsLogger.e(TAG, "renderFeedAd[Render]: no ad object");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        try {
            View adView = ad.getAdView();
            if (adView != null) {
                if (listener != null) listener.onFeedAdRendered();
            }
            FsLogger.d(TAG, "renderFeedAd[Render]: " + (adView != null ? "returning adView" : "adView null"));
            return adView;
        } catch (Exception e) {
            FsLogger.e(TAG, "renderFeedAd[Render] exception: " + e.getMessage(), e);
            if (listener != null)
                listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常"));
            return null;
        }
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
        TTFeedAd ad = (TTFeedAd) response.getNativeAd();
        if (ad == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: nativeAd is null");
            return;
        }
        try {
            ViewGroup container = containerView;
            // 若未传入 containerView，尝试从 clickableViews 中推断
            if (container == null && clickableViews != null && !clickableViews.isEmpty()) {
                View first = clickableViews.get(0);
                container = first instanceof ViewGroup ? (ViewGroup) first : null;
                if (container == null && first.getParent() instanceof ViewGroup) {
                    container = (ViewGroup) first.getParent();
                }
                if (container == null) {
                    container = new FrameLayout(first.getContext());
                }
            }
            if (container == null) {
                FsLogger.e(TAG, "registerNativeAdInteraction: no container available");
                return;
            }

            List<View> views = clickableViews != null ? new ArrayList<>(clickableViews) : new ArrayList<>();
            if (!views.contains(container)) {
                views.add(container);
            }

            ad.registerViewForInteraction(container, views, null,
                    new TTNativeAd.AdInteractionListener() {
                        @Override
                        public void onAdClicked(View v, TTNativeAd na) {
                            FsLogger.d(TAG, "TTFeedAd.onAdClicked: sourceId=" + response.getSdkName());
                            if (listener != null) listener.onAdClick();
                        }

                        @Override
                        public void onAdCreativeClick(View v, TTNativeAd na) {
                        }

                        @Override
                        public void onAdShow(TTNativeAd na) {
                            FsLogger.d(TAG, "TTFeedAd.onAdShow: sourceId=" + response.getSdkName());
                            if (listener != null) listener.onAdShow();
                        }
                    });
            FsLogger.d(TAG, "registerNativeAdInteraction: registered " + views.size() + " clickable views");
        } catch (Exception e) {
            FsLogger.w(TAG, "registerNativeAdInteraction: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  IFsNativeMaterialProvider — 从 TTFeedAd 原生对象提取素材
    // ════════════════════════════════════════════════════════════════

    @Override
    public String getTitle(Object nativeAd) {
        return nativeAd instanceof TTFeedAd ? ((TTFeedAd) nativeAd).getTitle() : null;
    }

    @Override
    public String getDescription(Object nativeAd) {
        return nativeAd instanceof TTFeedAd ? ((TTFeedAd) nativeAd).getDescription() : null;
    }

    @Override
    public String getIconUrl(Object nativeAd) {
        if (!(nativeAd instanceof TTFeedAd)) return null;
        TTImage icon = ((TTFeedAd) nativeAd).getIcon();
        return icon != null ? icon.getImageUrl() : null;
    }

    @Override
    public String getImageUrl(Object nativeAd) {
        if (!(nativeAd instanceof TTFeedAd)) return null;
        List<TTImage> images = ((TTFeedAd) nativeAd).getImageList();
        if (images != null && !images.isEmpty() && images.get(0) != null) {
            return images.get(0).getImageUrl();
        }
        return null;
    }

    @Override
    public List<String> getImageList(Object nativeAd) {
        if (!(nativeAd instanceof TTFeedAd)) return Collections.emptyList();
        List<TTImage> images = ((TTFeedAd) nativeAd).getImageList();
        if (images == null) return Collections.emptyList();
        List<String> urls = new ArrayList<>();
        for (TTImage img : images) {
            if (img != null) urls.add(img.getImageUrl());
        }
        return urls;
    }

    @Override
    public String getCallToAction(Object nativeAd) {
        return nativeAd instanceof TTFeedAd ? ((TTFeedAd) nativeAd).getButtonText() : null;
    }

    @Override
    public double getRating(Object nativeAd) {
        if (!(nativeAd instanceof TTFeedAd)) return 0.0;
        try {
            return ((TTFeedAd) nativeAd).getAppScore();
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public String getVideoUrl(Object nativeAd) {
        if (!(nativeAd instanceof TTFeedAd)) return null;
        try {
            return ((TTFeedAd) nativeAd).getVideoCoverImage() != null
                    ? ((TTFeedAd) nativeAd).getVideoCoverImage().getImageUrl() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getImageWidth(Object nativeAd) {
        if (!(nativeAd instanceof TTFeedAd)) return 0;
        try {
            List<TTImage> images = ((TTFeedAd) nativeAd).getImageList();
            return (images != null && !images.isEmpty() && images.get(0) != null)
                    ? images.get(0).getWidth() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getImageHeight(Object nativeAd) {
        if (!(nativeAd instanceof TTFeedAd)) return 0;
        try {
            List<TTImage> images = ((TTFeedAd) nativeAd).getImageList();
            return (images != null && !images.isEmpty() && images.get(0) != null)
                    ? images.get(0).getHeight() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public AdInteractionType getInteractionType(Object nativeAd) {
        if (!(nativeAd instanceof TTFeedAd)) return AdInteractionType.UNKNOWN;
        int rawType = ((TTFeedAd) nativeAd).getInteractionType();
        // Pangle: BROWSER(2)→LANDING_PAGE, LANDING_PAGE(3)→LANDING_PAGE, DOWNLOAD(4)→DOWNLOAD
        if (rawType == 2 || rawType == 3) return AdInteractionType.LANDING_PAGE;
        if (rawType == 4) return AdInteractionType.DOWNLOAD;
        return AdInteractionType.UNKNOWN;
    }

    @Override
    public long getAppSize(Object nativeAd) {
        return nativeAd instanceof TTFeedAd ? ((TTFeedAd) nativeAd).getAppSize() : 0L;
    }

    @Override
    public AdAppMiitInfo getAppMiitInfo(Object nativeAd) {
        if (!(nativeAd instanceof TTFeedAd)) return null;
        try {
            ComplianceInfo info = ((TTFeedAd) nativeAd).getComplianceInfo();
            if (info == null) return null;
            return new AdAppMiitInfo.Builder()
                    .appName(info.getAppName())
                    .developerName(info.getDeveloperName())
                    .appVersion(info.getAppVersion())
                    .privacyUrl(info.getPrivacyUrl())
                    .permissionUrl(info.getPermissionUrl())
                    .functionDescUrl(info.getFunctionDescUrl())
                    .build();
        } catch (Exception e) {
            FsLogger.w(TAG, "getAppMiitInfo: " + e.getMessage());
            return null;
        }
    }
}
