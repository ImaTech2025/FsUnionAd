package com.ima.union.core.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.ima.union.core.model.UnionAdResponse;

import java.util.List;

/**
 * 信息流广告展示接口。
 *
 * <p>所有信息流展示（模板渲染 / 自渲染）都通过此接口完成：</p>
 * <ul>
 *   <li><b>模板渲染</b>：适配器在 {@link #renderFeedAd} 中调用 SDK 渲染，返回 View</li>
 *   <li><b>自渲染</b>：适配器通过 listener 回调素材数据，宿主自行构建 UI，返回 null</li>
 * </ul>
 *
 * <p>与 {@link AdAdapter#request} 的关系：request() 负责加载，renderFeedAd() 负责渲染。
 * 两者按此职责分离，互不包含。</p>
 */
public interface FeedAdAdapter extends AdAdapter {

    /**
     * 渲染信息流广告 View。
     *
     * @param context  上下文
     * @param response 加载完成的广告响应
     * @param listener 渲染/展示/点击事件回调
     * @return 渲染后的 View；自渲染场景可返回 null（由宿主自行构建）
     */
    View renderFeedAd(Context context, UnionAdResponse response, FeedAdListener listener);

    /**
     * 为自渲染广告注册点击交互监听。
     *
     * <p>宿主自行构建 UI 后调用此方法，将点击区域 View 注册到平台 SDK，
     * 使 SDK 能够正确处理点击事件并触发回调（曝光/点击等）。</p>
     *
     * <p>默认空实现；各平台适配器应覆写，调用对应 SDK 的点击注册 API：</p>
     * <ul>
     *   <li>Pangle: TTFeedAd.registerViewForInteraction()</li>
     *   <li>Baidu:  NativeResponse.registerViewForInteraction()</li>
     *   <li>GDT:    NativeUnifiedADData.bindAdToView()</li>
     * </ul>
     *
     * @param response       广告响应对象（包含原生广告对象 response.getNativeAd()）
     * @param containerView  广告容器（用作点击事件的锚点 ViewGroup）
     * @param clickableViews 可点击的 View 列表
     * @param listener       信息流事件回调（曝光/点击/错误）
     */
    default void registerNativeAdInteraction(UnionAdResponse response,
                                              ViewGroup containerView,
                                              List<View> clickableViews,
                                              FeedAdListener listener) {
        // 默认空实现，由各平台适配器覆写
    }
}
