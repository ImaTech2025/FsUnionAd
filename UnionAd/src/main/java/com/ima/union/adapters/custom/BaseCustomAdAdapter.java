package com.ima.union.adapters.custom;

import android.content.Context;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdCallback;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.FsAdErrorCode;

/**
 * 自定义广告适配器基类。
 *
 * <p><b>@deprecated</b> 自 v1.1.0 起，推荐使用格式特定的自定义适配器抽象类，按广告格式职责分离：</p>
 * <ul>
 *   <li>{@link FsCustomSplashAdapter} — 开屏广告</li>
 *   <li>{@link FsCustomInterstitialAdapter} — 插屏广告</li>
 *   <li>{@link FsCustomRewardedVideoAdapter} — 激励视频广告</li>
 *   <li>{@link FsCustomFeedAdapter} — 信息流广告（模板+自渲染）</li>
 * </ul>
 *
 * <p>接入方可继承对应格式的抽象类实现自定义广告平台适配，每个子类职责单一、接口清晰。</p>
 *
 * @deprecated 使用 {@link FsCustomSplashAdapter} / {@link FsCustomInterstitialAdapter} /
 *             {@link FsCustomRewardedVideoAdapter} / {@link FsCustomFeedAdapter} 代替。
 */
@Deprecated
public abstract class BaseCustomAdAdapter implements AdAdapter {

    /**
     * 返回该适配器的平台类型标识。
     * <p>子类必须通过 {@link AdSdkType#of(String, String)} 注册自己的自定义平台类型并返回，
     * 支持媒体接入多个自定义广告源时各自拥有独立标识，避免全部挤在一个桶里。</p>
     */
    @Override
    public abstract AdSdkType getSdkType();

    /**
     * 返回该适配器对应的 SDK 名称，必须与策略 JSON 中该广告源的 {@code sdkName} 字段一致。
     * <p>多个自定义适配器的 sdkName 必须唯一，否则后注册的会覆盖先注册的。</p>
     */
    @Override
    public abstract String getSdkName();

    @Override
    public abstract String getAdapterVersion();

    @Override
    public boolean supportBidding() {
        return false;
    }

    /**
     * 自定义适配器默认不支持竞价；如需支持可重写此方法。
     */
    @Override
    public void request(Context context, AdRequestParams params, AdSourceConfig sourceConfig, AdCallback callback) {
        callback.onLoadFailed(
                FsAdErrorCode.REQUEST_PARAM_INVALID,
                FsAdErrorCode.buildMsg("适配器未实现request()", getSdkName(), 0, "")
        );
    }
}
