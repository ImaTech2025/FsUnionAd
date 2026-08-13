package com.ima.union.adapters.gdt;

import android.content.Context;
import android.view.View;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.AdLoadTimeout;
import com.ima.union.core.adapter.FeedAdAdapter;
import com.ima.union.core.adapter.FeedAdListener;
import com.ima.union.core.adapter.FsUnionListenerBridge;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.utils.FsLogger;
import com.qq.e.ads.nativ.ADSize;
import com.qq.e.ads.nativ.NativeExpressAD;
import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.e.ads.nativ.NativeExpressMediaListener;
import com.qq.e.comm.util.AdError;

import java.util.List;

/**
 * 优量汇信息流模板广告适配器。
 *
 * <p>通过 {@link NativeExpressAD} 加载模板广告，返回 {@link NativeExpressADView}，
 * 宿主调用 renderFeedAd 获取 View 后直接添加到容器即可。</p>
 *
 * <p><b>回调桥接机制</b>：{@link NativeExpressAD} 的曝光/点击回调只在构造时绑定，
 * 无法在渲染阶段重新注册。通过 {@link FsUnionListenerBridge} 将请求阶段构造的
 * {@link NativeExpressAD.NativeExpressADListener} 与渲染阶段的 {@link FeedAdListener}
 * 连接起来，经由 {@link UnionAdResponse#getExtra()} 传递。</p>
 *
 * <p><b>广告尺寸</b>：默认全宽自适应高（{@link ADSize#FULL_WIDTH} × {@link ADSize#AUTO_HEIGHT}），
 * 可通过 {@link AdRequestParams#getExpressViewAcceptedSize} 传入固定尺寸（dp）。</p>
 */
public class GdtFeedTemplateAdapter extends GdtBaseAdapter implements FeedAdAdapter {

    private static final String TAG = "GdtFeedTemplateAdapter";

    @Override
    protected void doRequest(Context context, AdRequestParams params,
                             AdSourceConfig sourceConfig, AdCallback callback) {
        String sdkName = sourceConfig.getSdkName();
        String placeId = resolvePlaceId(sourceConfig);
        FsLogger.d(TAG, "▶ request[FeedTemplate]: sdkName=" + sdkName + " placeId=" + placeId
                + " bidFloor=" + sourceConfig.getBidFloor() + " timeout=" + sourceConfig.getTimeout());

        try {
            // 确定广告尺寸：优先使用 expressViewAcceptedSize（dp），否则全宽自适应高
            int adWidth = ADSize.FULL_WIDTH;
            int adHeight = ADSize.AUTO_HEIGHT;
            if (params != null) {
                int[] expressSize = params.getExpressViewAcceptedSize();
                if (expressSize != null) {
                    adWidth = expressSize[0];
                    adHeight = expressSize[1];
                    FsLogger.d(TAG, "request[FeedTemplate]: using expressSize=" + adWidth + "x" + adHeight + " dp");
                }
            }

            ADSize adSize = new ADSize(adWidth, adHeight);
            AdLoadTimeout timeoutCtrl = new AdLoadTimeout(sourceConfig.getTimeout(), sdkName, callback);
            timeoutCtrl.start();

            // 桥接器：请求阶段构造，渲染阶段注入 listener
            FsUnionListenerBridge<FeedAdListener> bridge = new FsUnionListenerBridge<>();

            NativeExpressAD expressAD = new NativeExpressAD(
                    context, adSize, placeId,
                    new NativeExpressAD.NativeExpressADListener() {

                        @Override
                        public void onADLoaded(List<NativeExpressADView> adViews) {
                            timeoutCtrl.finish();
                            if (adViews != null && !adViews.isEmpty()) {
                                NativeExpressADView adView = adViews.get(0);
                                FsLogger.d(TAG, "request[FeedTemplate]: onADLoaded, sourceId=" + sdkName);
                                // 将桥接器打包到 extra，渲染阶段注入 listener
                                java.util.Map<String, Object> extra = new java.util.HashMap<>();
                                extra.put(FsUnionListenerBridge.EXTRA_KEY, bridge);
                                UnionAdResponse response = buildResponse(
                                        sourceConfig, AdFormat.FEED_TEMPLATE, adView, 0, extra);
                                if (callback != null)
                                    callback.onLoaded(response);
                            } else {
                                if (callback != null)
                                    callback.onLoadFailed(FsAdErrorCode.SDK_NO_AD_RETURNED, FsAdErrorCode.buildMsg("SDK加载失败:无广告返回", sdkName, 0, ""));
                            }
                        }

                        @Override
                        public void onRenderFail(NativeExpressADView adView) {
                            FsLogger.e(TAG, "request[FeedTemplate]: onRenderFail, sourceId=" + sdkName);
                            FeedAdListener l = bridge.get();
                            if (l != null)
                                l.onAdError(FsAdErrorCode.RENDER_FAILED, FsAdErrorCode.buildMsg("模板渲染失败"));
                        }

                        @Override
                        public void onRenderSuccess(NativeExpressADView adView) {
                            FsLogger.d(TAG, "GdtNativeExpress.onRenderSuccess: sourceId=" + sdkName);
                            FeedAdListener l = bridge.get();
                            if (l != null) l.onFeedAdRendered();
                        }

                        @Override
                        public void onADExposure(NativeExpressADView adView) {
                            FsLogger.d(TAG, "GdtNativeExpress.onADExposure: sourceId=" + sdkName);
                            FeedAdListener l = bridge.get();
                            if (l != null) l.onAdShow();
                        }

                        @Override
                        public void onADClicked(NativeExpressADView adView) {
                            FsLogger.d(TAG, "GdtNativeExpress.onADClicked: sourceId=" + sdkName);
                            FeedAdListener l = bridge.get();
                            if (l != null) l.onAdClick();
                        }

                        @Override
                        public void onADClosed(NativeExpressADView adView) {
                            FsLogger.d(TAG, "GdtNativeExpress.onADClosed: sourceId=" + sdkName);
                            FeedAdListener l = bridge.get();
                            if (l != null) l.onAdClose();
                        }

                        @Override
                        public void onADLeftApplication(NativeExpressADView adView) {
                        }

                        @Override
                        public void onNoAD(AdError error) {
                            FsLogger.w(TAG, "request[FeedTemplate]: onNoAD, sourceId=" + sdkName
                                    + " code=" + error.getErrorCode() + " msg=" + error.getErrorMsg());
                            timeoutCtrl.finish();
                            if (callback != null)
                                callback.onLoadFailed(FsAdErrorCode.SDK_LOAD_FAILED, FsAdErrorCode.buildMsg("SDK加载失败", sdkName, error.getErrorCode(), error.getErrorMsg()));
                        }
                    });

            expressAD.loadAD(1);
        } catch (Exception e) {
            FsLogger.e(TAG, "request[FeedTemplate] exception: " + e.getMessage(), e);
            if (callback != null)
                callback.onLoadFailed(FsAdErrorCode.REQUEST_EXCEPTION, FsAdErrorCode.buildMsg("请求异常", sdkName, 0, e.getMessage()));
        }
    }

    /**
     * 渲染信息流模板广告。
     *
     * <p>从 {@link UnionAdResponse#getExtra()} 中取出 {@link FsUnionListenerBridge}，
     * 将当前 listener 注入后触发 {@link NativeExpressADView#render()} 异步渲染。
     * 渲染成功/失败、曝光、点击等回调通过桥接器转发。</p>
     */
    @Override
    public View renderFeedAd(Context context, UnionAdResponse response, FeedAdListener listener) {
        FsLogger.d(TAG, "▶ renderFeedAd[Template]: sourceId=" + response.getSdkName());
        if (!(response.getNativeAd() instanceof NativeExpressADView)) {
            FsLogger.e(TAG, "renderFeedAd[Template]: nativeAd is not NativeExpressADView");
            if (listener != null)
                listener.onAdError(FsAdErrorCode.AD_OBJECT_INVALID, FsAdErrorCode.buildMsg("广告对象无效"));
            return null;
        }
        NativeExpressADView adView = (NativeExpressADView) response.getNativeAd();
        try {
            // 将当前 listener 注入桥接器，使请求阶段的 NativeExpressADListener 能转发回调
            java.util.Map<String, Object> extra = response.getExtra();
            if (extra != null) {
                FsUnionListenerBridge<FeedAdListener> bridge = (FsUnionListenerBridge<FeedAdListener>) extra.get(FsUnionListenerBridge.EXTRA_KEY);
                if (bridge != null) bridge.bind(listener);
            }

            // 注册视频播放回调（非视频广告为空实现）
            adView.setMediaListener(new NativeExpressMediaListener() {
                @Override
                public void onVideoInit(NativeExpressADView view) {
                }

                @Override
                public void onVideoLoading(NativeExpressADView view) {
                }

                @Override
                public void onVideoCached(NativeExpressADView view) {
                }

                @Override
                public void onVideoReady(NativeExpressADView view, long duration) {
                }

                @Override
                public void onVideoStart(NativeExpressADView view) {
                }

                @Override
                public void onVideoPause(NativeExpressADView view) {
                }

                @Override
                public void onVideoComplete(NativeExpressADView view) {
                }

                @Override
                public void onVideoError(NativeExpressADView view, AdError error) {
                    FsLogger.e(TAG, "GdtNativeExpress.onVideoError: " + error.getErrorMsg());
                }

                @Override
                public void onVideoPageOpen(NativeExpressADView view) {
                }

                @Override
                public void onVideoPageClose(NativeExpressADView view) {
                }
            });

            // 触发模板渲染（异步；渲染完成后 SDK 回调 onRenderSuccess/onRenderFail）
            adView.render();
            FsLogger.d(TAG, "renderFeedAd[Template]: render() called");
            return adView;
        } catch (Exception e) {
            FsLogger.e(TAG, "renderFeedAd[Template] exception: " + e.getMessage(), e);
            if (listener != null)
                listener.onAdError(FsAdErrorCode.SHOW_EXCEPTION, FsAdErrorCode.buildMsg("展示异常", response.getSdkName(), 0, e.getMessage()));
            return null;
        }
    }
}
