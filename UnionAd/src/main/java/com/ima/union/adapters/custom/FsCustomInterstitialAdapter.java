package com.ima.union.adapters.custom;


import com.ima.union.utils.FsLogger;
import android.content.Context;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.InterstitialAdAdapter;
import com.ima.union.core.adapter.InterstitialAdListener;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;

/**
 * 自定义插屏广告适配器抽象类。
 *
 * <p>外部接入方继承此类实现自定义广告平台的插屏广告接入。
 * 需要实现的方法：</p>
 * <ul>
 *   <li>{@link #getSdkName()} — 返回自定义 SDK 名称，需与策略 JSON 中 sdkName 一致</li>
 *   <li>{@link #getAdapterVersion()} — 返回适配器版本</li>
 *   <li>{@link #request(Context, AdRequestParams, AdSourceConfig, AdCallback)} — 加载广告</li>
 *   <li>{@link #showInterstitial(Context, UnionAdResponse, InterstitialAdListener)} — 展示广告</li>
 * </ul>
 *
 * @see BaseCustomAdAdapter
 * @see InterstitialAdAdapter
 */
public abstract class FsCustomInterstitialAdapter extends BaseCustomAdAdapter
        implements InterstitialAdAdapter {

    private static final String TAG = "FsCustomInterstitial";

    /**
     * 加载插屏广告。
     * <p>加载成功后通过 {@code callback.onLoaded(UnionAdResponse)} 回调，
     * 失败通过 {@code callback.onLoadFailed(errorCode, errorMsg)} 回调。
     * 如果自定义 SDK 没有独立的素材缓存回调，建议在 {@code onLoaded} 后立即调用
     * {@code callback.onCachedSuccess(UnionAdResponse)}。</p>
     */
    @Override
    public abstract void request(Context context, AdRequestParams params,
                                  AdSourceConfig sourceConfig, AdCallback callback);

    /**
     * 展示插屏广告。
     * <p>子类需展示广告并注册 {@code listener} 回调。</p>
     */
    @Override
    public abstract void showInterstitial(Context context, UnionAdResponse response,
                                           InterstitialAdListener listener);

    @Override
    public void destroy() {
        FsLogger.d(TAG, "Custom interstitial adapter [" + getSdkName() + "] destroyed");
    }
}
