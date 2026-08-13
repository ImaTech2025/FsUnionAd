package com.ima.union.adapters.gdt;

import com.ima.union.utils.FsLogger;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

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
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.qq.e.ads.nativ.NativeADEventListener;
import com.qq.e.ads.nativ.NativeADUnifiedListener;
import com.qq.e.ads.nativ.NativeUnifiedAD;
import com.qq.e.ads.nativ.NativeUnifiedADAppMiitInfo;
import com.qq.e.ads.nativ.NativeUnifiedADData;
import com.qq.e.ads.nativ.widget.NativeAdContainer;
import com.qq.e.comm.util.AdError;

import java.util.Collections;
import java.util.List;

/**
 * 优量汇信息流自渲染广告适配器。
 *
 * <p>通过 {@link IFsNativeMaterialProvider} 接口提供素材提取，支持点击交互注册。</p>
 */
public class GdtFeedRenderAdapter extends GdtBaseAdapter
        implements FeedAdAdapter, IFsNativeMaterialProvider {


    private static final String TAG = "GdtFeedRenderAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String placeId = resolvePlaceId(sourceConfig);
        FsLogger.d(TAG, "▶ request[FeedRender]: sdkName=" + sdkName + " placeId=" + placeId
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());
        try {
            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            NativeUnifiedAD nativeAd = new NativeUnifiedAD(context, placeId,
                    new NativeADUnifiedListener() {
                        @Override
                        public void onADLoaded(List<NativeUnifiedADData> adList) {
                            timeoutCtrl.finish();
                            if (adList != null && !adList.isEmpty()) {
                                NativeUnifiedADData data = adList.get(0);
                                double ecpm = data.getECPM();
                                FsLogger.d(TAG, "request[FeedRender]: onADLoaded ecpm=" + ecpm);

                                UnionAdResponse response = buildResponse(sourceConfig, AdFormat.FEED_RENDER,
                                        data, (int) ecpm);
                                if (callback != null) callback.onLoaded(response);
                            } else {
                                if (callback != null)
                                    callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sdkName, 0, ""));
                            }
                        }

                        @Override
                        public void onNoAD(AdError error) {
                            timeoutCtrl.finish();
                            if (callback != null)
                                callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, error.getErrorCode(), error.getErrorMsg()));
                        }
                    });
            nativeAd.loadData(1);
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
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        FsLogger.d(TAG, "▶ renderFeedAd[Render]: sourceId=" + response.getSdkName());
        NativeUnifiedADData data = (NativeUnifiedADData) response.getNativeAd();
        if (data == null) {
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        // 自渲染：素材由 IFsNativeMaterialProvider 提取，宿主自行构建 View，此处不返回 View
        if (listener != null) listener.onFeedAdRendered();
        return null;
    }

    @Override
    public void registerNativeAdInteraction(UnionAdResponse response,
                                            ViewGroup containerView,
                                            List<View> clickableViews,
                                            FeedAdListener listener) {
        if (response == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: response is null");
            return;
        }
        if (containerView == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: containerView is null");
            return;
        }
        NativeUnifiedADData data = (NativeUnifiedADData) response.getNativeAd();
        if (data == null) {
            FsLogger.e(TAG, "registerNativeAdInteraction: nativeAd is null");
            return;
        }
        try {
            // 4.561+ bindAdToView 只接受 NativeAdContainer (extends FrameLayout)
            // 将接入方的 ViewGroup 包装到 NativeAdContainer 中完成交互注册
            NativeAdContainer container = new NativeAdContainer(containerView.getContext());
            if (containerView.getParent() instanceof ViewGroup) {
                ((ViewGroup) containerView.getParent()).removeView(containerView);
            }
            container.addView(containerView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (clickableViews != null) {
                data.bindAdToView(containerView.getContext(), container, null, clickableViews);
            }
            data.setNativeAdEventListener(new NativeADEventListener() {
                @Override
                public void onADExposed() {
                    FsLogger.d(TAG, "GdtNative.onADExposed: sourceId=" + response.getSdkName());
                    if (listener != null) listener.onAdShow();
                }

                @Override
                public void onADClicked() {
                    FsLogger.d(TAG, "GdtNative.onADClicked: sourceId=" + response.getSdkName());
                    if (listener != null) listener.onAdClick();
                }

                @Override
                public void onADError(AdError error) {
                    FsLogger.e(TAG, "GdtNative.onADError: " + error.getErrorMsg());
                    if (listener != null)
                        listener.onAdError(FsAdErrorCode.SDK_SHOW_ERROR, FsAdErrorCode.buildMsg("SDK展示错误", response.getSdkName(), error.getErrorCode(), error.getErrorMsg()));
                }

                @Override
                public void onADStatusChanged() {
                }
            });
            FsLogger.d(TAG, "registerNativeAdInteraction");
        } catch (Exception e) {
            FsLogger.w(TAG, "registerNativeAdInteraction: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  IFsNativeMaterialProvider — 从 NativeUnifiedADData 提取素材
    // ════════════════════════════════════════════════════════════════

    @Override
    public String getTitle(Object nativeAd) {
        return nativeAd instanceof NativeUnifiedADData
                ? ((NativeUnifiedADData) nativeAd).getTitle() : null;
    }

    @Override
    public String getDescription(Object nativeAd) {
        return nativeAd instanceof NativeUnifiedADData
                ? ((NativeUnifiedADData) nativeAd).getDesc() : null;
    }

    @Override
    public String getIconUrl(Object nativeAd) {
        if (!(nativeAd instanceof NativeUnifiedADData)) return null;
        try {
            return ((NativeUnifiedADData) nativeAd).getIconUrl();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getImageUrl(Object nativeAd) {
        if (!(nativeAd instanceof NativeUnifiedADData)) return null;
        try {
            return ((NativeUnifiedADData) nativeAd).getImgUrl();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> getImageList(Object nativeAd) {
        if (!(nativeAd instanceof NativeUnifiedADData)) return Collections.emptyList();
        try {
            List<String> list = ((NativeUnifiedADData) nativeAd).getImgList();
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public int getImageWidth(Object nativeAd) {
        if (!(nativeAd instanceof NativeUnifiedADData)) return 0;
        try {
            return ((NativeUnifiedADData) nativeAd).getPictureWidth();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getImageHeight(Object nativeAd) {
        if (!(nativeAd instanceof NativeUnifiedADData)) return 0;
        try {
            return ((NativeUnifiedADData) nativeAd).getPictureHeight();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public String getCallToAction(Object nativeAd) {
        return nativeAd instanceof NativeUnifiedADData
                ? ((NativeUnifiedADData) nativeAd).getCTAText() : null;
    }

    @Override
    public double getRating(Object nativeAd) {
        if (!(nativeAd instanceof NativeUnifiedADData)) return 0.0;
        try {
            return ((NativeUnifiedADData) nativeAd).getAppScore();
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public AdInteractionType getInteractionType(Object nativeAd) {
        if (!(nativeAd instanceof NativeUnifiedADData)) return AdInteractionType.UNKNOWN;
        // GDT 无直接交互类型 API，根据 isAppAd() 区分下载/落地页
        return ((NativeUnifiedADData) nativeAd).isAppAd()
                ? AdInteractionType.DOWNLOAD : AdInteractionType.LANDING_PAGE;
    }

    @Override
    public long getAppSize(Object nativeAd) {
        return 0L; // GDT 不直接提供 App Size
    }

    @Override
    public AdAppMiitInfo getAppMiitInfo(Object nativeAd) {
        if (!(nativeAd instanceof NativeUnifiedADData)) return null;
        try {
            NativeUnifiedADAppMiitInfo info = ((NativeUnifiedADData) nativeAd).getAppMiitInfo();
            if (info == null) return null;
            return new AdAppMiitInfo.Builder()
                    .appName(info.getAppName())
                    .developerName(info.getAuthorName())
                    .appVersion(info.getVersionName())
                    .privacyUrl(info.getPrivacyAgreement())
                    .permissionUrl(info.getPermissionsUrl())
                    .functionDescUrl(info.getDescriptionUrl())
                    .build();
        } catch (Exception e) {
            FsLogger.w(TAG, "getAppMiitInfo: " + e.getMessage());
            return null;
        }
    }
}
