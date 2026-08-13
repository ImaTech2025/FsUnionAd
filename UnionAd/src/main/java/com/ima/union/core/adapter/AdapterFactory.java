package com.ima.union.core.adapter;

import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.utils.FsLogger;

/**
 * 自定义 Adapter 反射工厂。
 *
 * <p>通过后台下发的 {@code adapterClassName} 按需创建 Adapter 实例并注册到
 * {@link AdAdapterRegistry}，实现纯后台驱动广告源接入——无需客户端手动调
 * {@code registerCustomAdapter()}。</p>
 *
 * <p><b>安全性</b>：{@link Class#forName(String)} 只能加载 APK 内已打包的类，
 * Android 不允许动态加载外部 dex，因此不存在远程代码注入风险。</p>
 *
 * <p><b>线程安全</b>：{@code synchronized(AdapterFactory.class)} + 双重检查，
 * 保证同一 key 在多线程并发场景下只创建一次实例。</p>
 *
 * <p><b>使用流程</b>：</p>
 * <ol>
 *   <li>后台 JSON 下发 {@code "adapterClassName": "com.example.ads.MyAdapter"}</li>
 *   <li>{@link com.ima.union.core.strategy.StrategyUtils#resolveAdapter} registry miss</li>
 *   <li>调用 {@link #createIfAbsent(AdSourceConfig)} 反射创建 + 注册</li>
 *   <li>下次同 key 请求直接 registry 命中，不再走反射</li>
 * </ol>
 */
public final class AdapterFactory {

    private static final String TAG = "AdapterFactory";

    /**
     * 按需创建并注册自定义 Adapter。
     *
     * <p>仅当 {@link AdSourceConfig#getAdapterClassName()} 非空且 registry 中
     * 同 key 尚未注册时执行，否则立即返回。</p>
     *
     * @param sourceConfig 广告源配置（含 adapterClassName + adFormat）
     * @return Adapter 实例，创建/查找失败返回 null
     */
    public static AdAdapter createIfAbsent(AdSourceConfig sourceConfig) {
        String className = sourceConfig.getAdapterClassName();
        if (className == null || className.isEmpty()) {
            return null;
        }

        if (sourceConfig.getSdkType() == null) {
            FsLogger.w(TAG, "createIfAbsent: sdkType is null, cannot register");
            return null;
        }

        // 0. fast-path：锁外快速检查
        AdAdapterRegistry registry = AdAdapterRegistry.getInstance();
        AdAdapter existing = registry.getAdapter(sourceConfig.getSdkType(), sourceConfig.getAdFormat());
        if (existing != null) {
            return existing;
        }

        // 1. 慢路径：双重检查锁
        synchronized (AdapterFactory.class) {
            // 二次检查：抢锁期间可能已被其他线程创建
            existing = registry.getAdapter(sourceConfig.getSdkType(), sourceConfig.getAdFormat());
            if (existing != null) {
                return existing;
            }

            try {
                FsLogger.i(TAG, "Creating adapter via reflection: " + className);
                Class<?> clazz = Class.forName(className);
                Object instance = clazz.newInstance();
                if (!(instance instanceof AdAdapter)) {
                    FsLogger.e(TAG, "Class " + className + " does not implement AdAdapter");
                    return null;
                }
                AdAdapter adapter = (AdAdapter) instance;

                // 校验 sdkType 一致性：反射创建的 adapter 的 sdkType 必须与配置一致
                if (!adapter.getSdkType().getKey().equals(sourceConfig.getSdkType().getKey())) {
                    FsLogger.e(TAG, "Adapter sdkType mismatch: expected " + sourceConfig.getSdkType().getKey()
                            + " but got " + adapter.getSdkType().getKey());
                    return null;
                }

                registry.registerCustomAdapter(adapter, sourceConfig.getAdFormat());
                return adapter;
            } catch (ClassNotFoundException e) {
                FsLogger.e(TAG, "Adapter class not found: " + className
                        + ". Make sure the class is packaged in the APK.");
            } catch (InstantiationException | IllegalAccessException e) {
                FsLogger.e(TAG, "Failed to instantiate adapter: " + className
                        + " (need public no-arg constructor): " + e.getMessage());
            } catch (ClassCastException e) {
                FsLogger.e(TAG, "Class " + className + " does not implement AdAdapter");
            } catch (Exception e) {
                FsLogger.e(TAG, "Unexpected error creating adapter: " + className, e);
            }
            return null;
        }
    }

    private AdapterFactory() {}
}
