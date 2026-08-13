package com.ima.union.manager;
import com.ima.union.utils.FsLogger;
import com.ima.union.utils.FsLogger;
import android.content.Context;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.config.CloudStrategyManager;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.AdUnitConfig;
import com.ima.union.core.model.FsAdErrorCode;
import com.ima.union.core.model.UnionAdResponse;
import com.ima.union.core.model.entry.IFsUnionNativeAd;
import com.ima.union.core.model.impl.FsUnionNativeAd;
import com.ima.union.core.strategy.AdStrategyCallback;
import com.ima.union.core.strategy.AdStrategyManager;
import com.ima.union.core.strategy.StrategyUtils;

/**
 * 信息流自渲染广告管理器 — 纯静态工具类。
 * <p>每次 {@code loadAd} 都是一次独立请求,不会拦截同 slotId 的并发请求。</p>
 */
public class FsFeedRenderAdManager {

    private static final String TAG = "FsFeedRenderAdManager";

        public interface OnFsNativeAdLoadListener {
        void onAdLoaded(IFsUnionNativeAd ad);
        void onAdLoadError(int errorCode, String errorMsg);
    }

    public static void loadAd(Context context, AdRequestParams params,
                              OnFsNativeAdLoadListener loadListener) {
        String slotId = params.getSlotId();
        FsLogger.d("FeedRdAdMgr", "▶ loadAd: slotId=" + slotId);

        final AdUnitConfig mergedUnitConfig = CloudStrategyManager.getInstance()
                .getMergedUnitConfig(slotId, params.getDefaultStrategyJson());

        if (mergedUnitConfig == null) {
            FsLogger.e("FeedRdAdMgr", "loadAd: merged config is null, slotId=" + slotId);
            if (loadListener != null)
                loadListener.onAdLoadError(FsAdErrorCode.CONFIG_NULL, FsAdErrorCode.buildMsg("合并配置为空"));
            return;
        }

        mergedUnitConfig.setAdFormat(AdFormat.FEED_RENDER);
        AdStrategyManager.getInstance().execute(context, params, mergedUnitConfig,
                new AdStrategyCallback() {
                    @Override
                    public void onAdLoaded(UnionAdResponse response) {
                        AdSourceConfig sourceConfig = findSource(mergedUnitConfig, response.getAdUnitId());
                        AdAdapter adapter = sourceConfig != null
                                ? StrategyUtils.resolveAdapter(sourceConfig)
                                : null;
                        if (adapter == null) {
                            FsLogger.e("FeedRdAdMgr", "onAdLoaded: cannot resolve adapter for " + response.getSdkName());
                            if (loadListener != null)
                                loadListener.onAdLoadError(FsAdErrorCode.ADAPTER_RESOLVE_FAILED, FsAdErrorCode.buildMsg("无法解析适配器"));
                            return;
                        }
                        IFsUnionNativeAd ad = new FsUnionNativeAd(slotId, response, adapter);
                        FsLogger.i(TAG, "loadAd success: " + response.getSdkName()
                                + " ecpm=" + ad.getEcpm() + " slotId=" + slotId);
                        if (loadListener != null) loadListener.onAdLoaded(ad);
                    }

                    @Override
                    public void onNoFill(String reason) {
                        FsLogger.w("FeedRdAdMgr", "loadAd: no fill, " + reason);
                        if (loadListener != null)
                            loadListener.onAdLoadError(FsAdErrorCode.NO_FILL, reason);
                    }
                });
    }

    private static AdSourceConfig findSource(AdUnitConfig config, String adUnitId) {
        if (config == null || config.getSources() == null) return null;
        for (AdSourceConfig s : config.getSources()) {
            if (adUnitId != null && adUnitId.equals(s.getAdUnitId())) return s;
        }
        return null;
    }
}
