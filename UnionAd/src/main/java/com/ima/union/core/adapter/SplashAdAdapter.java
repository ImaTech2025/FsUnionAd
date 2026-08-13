package com.ima.union.core.adapter;

import android.content.Context;
import android.view.ViewGroup;
import com.ima.union.core.model.UnionAdResponse;

/**
 * 开屏广告适配器接口。
 *
 * 设计要点：
 *   showSplash 的 ViewGroup container 由外部调用方传入（如 Activity 的 FrameLayout）。
 *   适配器不自行创建或查找容器，只负责将广告 View 渲染到传入的 container 中。
 */
public interface SplashAdAdapter extends AdAdapter {
    /**
     * @param context   上下文，应为 Activity
     * @param response  已加载的广告响应
     * @param container 外部传入的展示容器（ViewGroup）
     * @param listener  回调
     */
    void showSplash(Context context, UnionAdResponse response, ViewGroup container, SplashAdListener listener);
}
