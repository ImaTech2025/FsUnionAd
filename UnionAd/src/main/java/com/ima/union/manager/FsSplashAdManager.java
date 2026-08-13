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
import com.ima.union.core.model.entry.IFsUnionSplashAd;
import com.ima.union.core.model.impl.FsUnionSplashAd;
import com.ima.union.core.strategy.AdStrategyCallback;
import com.ima.union.core.strategy.AdStrategyManager;
import com.ima.union.core.strategy.StrategyUtils;

/**
 * 开屏广告管理器 — 纯静态工具类。
 * <p>提供 {@link #loadAd} 静态方法加载开屏广告,加载成功后通过回调返回 {@link FsUnionSplashAd} 广告对象。
 * 每次 {@code loadAd} 都会发起一次全新的请求,不会与历史请求互相影响。</p>
 */
public class FsSplashAdManager {

    private static final String TAG = "FsSplashAdManager";

        /**
     * 开屏广告加载回调。
     */
    public interface OnFsSplashAdLoadListener {
        /** 广告加载成功,返回广告对象(对外接口类型,业务方无需关心实现类) */
        void onAdLoaded(IFsUnionSplashAd ad);
        /** 广告加载失败 */
        void onAdLoadError(int errorCode, String errorMsg);
    }

    /**
     * 加载开屏广告。
     * <p>每次调用都是一次独立请求,不会拦截同 slotId 的并发请求。</p>
     *
     * @param context      上下文
     * @param params       请求参数,必须包含 slotId
     * @param loadListener 加载结果回调(加载成功返回广告对象,失败返回错误码和错误信息)
     */
    public static void loadAd(Context context, AdRequestParams params,
                              OnFsSplashAdLoadListener loadListener) {
        String slotId = params.getSlotId();
        FsLogger.d(TAG, "▶ loadAd: slotId=" + slotId
                + " hasDefaultStrategy=" + (params.getDefaultStrategyJson() != null));

        final AdUnitConfig mergedUnitConfig = CloudStrategyManager.getInstance()
                .getMergedUnitConfig(slotId, params.getDefaultStrategyJson());

        if (mergedUnitConfig == null) {
            FsLogger.e("FsSplashAdManager", "loadAd: merged config is null, slotId=" + slotId);
            if (loadListener != null)
                loadListener.onAdLoadError(FsAdErrorCode.CONFIG_NULL, FsAdErrorCode.buildMsg("合并配置为空"));
            return;
        }

        mergedUnitConfig.setAdFormat(AdFormat.SPLASH);
        AdStrategyManager.getInstance().execute(context, params, mergedUnitConfig,
                new AdStrategyCallback() {
                    @Override
                    public void onAdLoaded(UnionAdResponse response) {
                        // adUnitId 是唯一确定性标识(同 SDK 多 adUnitId 场景下仅靠 sdkName 会撞车)
                        AdSourceConfig sourceConfig = findSource(mergedUnitConfig, response.getAdUnitId());
                        AdAdapter adapter = sourceConfig != null
                                ? StrategyUtils.resolveAdapter(sourceConfig)
                                : null;
                        if (adapter == null) {
                            FsLogger.e("FsSplashAdManager", "onAdLoaded: cannot resolve adapter for " + response.getSdkName());
                            if (loadListener != null)
                                loadListener.onAdLoadError(FsAdErrorCode.ADAPTER_RESOLVE_FAILED, FsAdErrorCode.buildMsg("无法解析适配器"));
                            return;
                        }
                        IFsUnionSplashAd ad = new FsUnionSplashAd(context, slotId, response, adapter);
                        FsLogger.i(TAG, "loadAd success: " + response.getSdkName()
                                + " ecpm=" + ad.getEcpm() + " slotId=" + slotId);
                        if (loadListener != null) loadListener.onAdLoaded(ad);
                    }

                    @Override
                    public void onNoFill(String reason) {
                        FsLogger.w("FsSplashAdManager", "loadAd: no fill, " + reason);
                        if (loadListener != null)
                            loadListener.onAdLoadError(FsAdErrorCode.NO_FILL, reason);
                    }
                });
    }

    // ── 私有工具方法 ──

    private static AdSourceConfig findSource(AdUnitConfig config, String adUnitId) {
        if (config == null || config.getSources() == null) return null;
        for (AdSourceConfig s : config.getSources()) {
            if (adUnitId != null && adUnitId.equals(s.getAdUnitId())) return s;
        }
        return null;
    }
}
