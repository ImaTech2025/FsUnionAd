package com.ima.union.utils;

import com.ima.union.BuildConfig;

/**
 * FsUnion SDK 版本号统一入口。
 *
 * <p>业务方通过 {@link #getVersion()} 或 {@link #getVersionName()} 获取当前集成的 SDK 版本,
 * 便于上报、日志、自检、AB 等场景使用。
 *
 * <p>版本号来源:
 * <ul>
 *   <li>根目录 {@code gradle.properties} 的 {@code VERSION_NAME} / {@code VERSION_CODE}</li>
 *   <li>编译期写入 {@link BuildConfig#SDK_VERSION} / {@link BuildConfig#SDK_VERSION_CODE}</li>
 *   <li>CI 可通过 {@code -PVERSION_NAME=... -PVERSION_CODE=...} 覆盖</li>
 * </ul>
 *
 * <p>本类为静态门面,无需持有 SDK 单例,不依赖 {@link com.ima.union.FsUnionSDK#initialize}
 * 即可读取——纯 BuildConfig 转发。
 */
public final class FsUnionSdkVersion {

    private FsUnionSdkVersion() {
        // no instance
    }

    /**
     * 获取 SDK 完整版本号字符串,例如 {@code "1.0.0-java"}。
     *
     * @return 永不返回 null(兜底返回 "unknown")
     */
    public static String getVersion() {
        String v = BuildConfig.SDK_VERSION;
        return (v == null || v.isEmpty()) ? "unknown" : v;
    }

    /**
     * 同 {@link #getVersion()},语义化方法名。
     */
    public static String getVersionName() {
        return getVersion();
    }

    /**
     * 获取整型构建号,用于 maven 制品区分或客户端升级判断。
     *
     * @return 永不返回 0 以下的值(BuildConfig 已保证 ≥ 1,此处冗余兜底)
     */
    public static int getVersionCode() {
        int c = BuildConfig.SDK_VERSION_CODE;
        return c > 0 ? c : 1;
    }
}
