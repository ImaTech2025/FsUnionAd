package com.ima.union.adapters.custom;


import com.ima.union.utils.FsLogger;
import android.content.Context;
import android.view.ViewGroup;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.SplashAdAdapter;
import com.ima.union.core.adapter.SplashAdListener;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;

/**
 * 自定义开屏广告适配器抽象类。
 *
 * <p>外部接入方继承此类实现自定义广告平台的开屏广告接入（如接入自有 DSP、私有广告平台等）。
 * 需要实现的方法：</p>
 * <ul>
 *   <li>{@link #getSdkName()} — 返回自定义 SDK 名称，需与策略 JSON 中 sdkName 一致</li>
 *   <li>{@link #getAdapterVersion()} — 返回适配器版本</li>
 *   <li>{@link #request(Context, AdRequestParams, AdSourceConfig, AdCallback)} — 加载广告</li>
 *   <li>{@link #showSplash(Context, UnionAdResponse, ViewGroup, SplashAdListener)} — 展示广告</li>
 * </ul>
 *
 * <p>注册方式：</p>
 * <pre>{@code
 * FsUnionSDK.registerCustomAdapter(new MyCustomSplashAdapter());
 * }</pre>
 *
 * @see BaseCustomAdAdapter
 * @see SplashAdAdapter
 */
public abstract class FsCustomSplashAdapter extends BaseCustomAdAdapter implements SplashAdAdapter {

    private static final String TAG = "FsCustomSplash";

    /**
     * 加载开屏广告。
     * <p>子类需调用三方 SDK 加载广告，加载成功后通过
     * {@code callback.onLoaded(UnionAdResponse)} 回调，失败通过
     * {@code callback.onLoadFailed(errorCode, errorMsg)} 回调。
     * 如果自定义 SDK 没有独立的素材缓存回调，建议在 {@code onLoaded} 后立即调用
     * {@code callback.onCachedSuccess(UnionAdResponse)}。</p>
     */
    @Override
    public abstract void request(Context context, AdRequestParams params,
                                  AdSourceConfig sourceConfig, AdCallback callback);

    /**
     * 展示开屏广告。
     * <p>子类需将广告渲染到 {@code container} 中，并注册 {@code listener} 回调。</p>
     */
    @Override
    public abstract void showSplash(Context context, UnionAdResponse response,
                                     ViewGroup container, SplashAdListener listener);

    @Override
    public void destroy() {
        FsLogger.d(TAG, "Custom splash adapter [" + getSdkName() + "] destroyed");
    }
}
