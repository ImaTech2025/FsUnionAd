package com.ima.union.adapters.custom;


import com.ima.union.utils.FsLogger;
import android.content.Context;
import android.view.View;

import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.adapter.FeedAdAdapter;
import com.ima.union.core.adapter.FeedAdListener;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.UnionAdResponse;

/**
 * 自定义信息流广告适配器抽象类（覆盖信息流模板和自渲染两种格式）。
 *
 * <p>外部接入方继承此类实现自定义广告平台的信息流广告接入。
 * 需要实现的方法：</p>
 * <ul>
 *   <li>{@link #getSdkName()} — 返回自定义 SDK 名称，需与策略 JSON 中 sdkName 一致</li>
 *   <li>{@link #getAdapterVersion()} — 返回适配器版本</li>
 *   <li>{@link #request(Context, AdRequestParams, AdSourceConfig, AdCallback)} — 加载广告</li>
 *   <li>{@link #renderFeedAd(Context, UnionAdResponse, FeedAdListener)} — 渲染广告 View</li>
 * </ul>
 *
 * <p>若需支持自渲染广告素材提取，子类可额外实现
 * {@link com.ima.union.core.adapter.IFsNativeMaterialProvider} 接口。</p>
 *
 * @see BaseCustomAdAdapter
 * @see FeedAdAdapter
 */
public abstract class FsCustomFeedAdapter extends BaseCustomAdAdapter implements FeedAdAdapter {

    private static final String TAG = "FsCustomFeed";

    /**
     * 加载信息流广告。
     * <p>加载成功后通过 {@code callback.onLoaded(UnionAdResponse)} 回调，
     * 失败通过 {@code callback.onLoadFailed(errorCode, errorMsg)} 回调。</p>
     */
    @Override
    public abstract void request(Context context, AdRequestParams params,
                                  AdSourceConfig sourceConfig, AdCallback callback);

    /**
     * 渲染信息流广告 View。
     * <p>子类需渲染广告并返回 View；如需回调展示/点击事件，应注册 {@code listener}。</p>
     *
     * @return 广告 View，自渲染广告若由宿主自行构建 UI 可返回 null
     */
    @Override
    public abstract View renderFeedAd(Context context, UnionAdResponse response,
                                       FeedAdListener listener);

    @Override
    public void destroy() {
        FsLogger.d(TAG, "Custom feed adapter [" + getSdkName() + "] destroyed");
    }
}
