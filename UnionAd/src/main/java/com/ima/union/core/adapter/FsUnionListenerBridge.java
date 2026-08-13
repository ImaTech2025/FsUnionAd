package com.ima.union.core.adapter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 跨阶段回调桥接器。
 *
 * <p>解决某些三方 SDK 回调必须在广告对象构造时注册、但业务监听器在后续阶段
 * （show/render）才可用的时序矛盾。</p>
 *
 * <p>用法：</p>
 * <ol>
 *   <li>请求阶段：创建 {@code FsUnionListenerBridge}，存入 {@link com.ima.union.core.model.UnionAdResponse#getExtra()} Map</li>
 *   <li>SDK 回调中：{@code bridge.get()} 获取已绑定监听器并转发</li>
 *   <li>展示阶段：从 extra 取出 bridge，{@code bridge.bind(listener)}</li>
 * </ol>
 *
 * <p>线程安全：内部使用 {@link AtomicReference}，支持异步加载和主线程展示的场景。</p>
 *
 * @param <T> 监听器类型
 */
public class FsUnionListenerBridge<T> {

    /**
     * 存入 {@link com.ima.union.core.model.UnionAdResponse#getExtra()} 的 key
     */
    public static final String EXTRA_KEY = "union.listener_bridge";

    private final AtomicReference<T> ref = new AtomicReference<>();

    /**
     * 绑定监听器。通常在 show/render 阶段由宿主调用。
     */
    public void bind(T listener) {
        ref.set(listener);
    }

    /**
     * 获取已绑定的监听器；未绑定时返回 null。
     */
    public T get() {
        return ref.get();
    }

    /**
     * 清除绑定，防止持有 Activity 等导致内存泄漏。
     * 通常在 {@code onAdClose/onAdDismissed} 回调中调用。
     */
    public void clear() {
        ref.set(null);
    }
}
