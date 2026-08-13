package com.ima.union.utils;

import android.content.Context;

import com.ima.union.FsUnionSDK;

/**
 * 适配器层公共工具方法。
 *
 * <p>各平台 BaseAdapter 在初始化时需要 appName 和 debug 配置，
 * 统一从此工具类获取，确保所有 SDK 取值来源一致。</p>
 */
public final class SdkUtils {

    private SdkUtils() {}

    /**
     * 获取应用名称。
     *
     * <p>优先级：</p>
     * <ol>
     *   <li>{@link FsUnionSDK.Config#appName}（业务方初始化时显式传入）</li>
     *   <li>通过 {@code PackageManager.getApplicationLabel()} 反射获取宿主应用名</li>
     * </ol>
     *
     * @param context 上下文，用于反射获取应用名
     * @return 应用名称，获取失败时返回空字符串
     */
    public static String resolveAppName(Context context) {
        // 1. 优先从全局 Config 取
        FsUnionSDK.Config config = FsUnionSDK.getGlobalConfig();
        if (config != null && config.appName != null && !config.appName.isEmpty()) {
            return config.appName;
        }
        // 2. 反射获取
        if (context == null) return "";
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(context.getPackageName(), 0);
            CharSequence label = pm.getApplicationLabel(appInfo);
            return label != null ? label.toString() : "";
        } catch (Exception e) {
            FsLogger.w("resolveAppName failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * 获取全局 debug 开关。
     *
     * <p>取 {@link FsUnionSDK.Config#debug} 的值。
     * 各平台 Adapter 初始化时统一调用此方法获取 debug 配置，
     * 不再各自维护 {@code debugMode} 字段。</p>
     *
     * @return true 表示开启调试模式
     */
    public static boolean isDebug() {
        FsUnionSDK.Config config = FsUnionSDK.getGlobalConfig();
        return config != null && config.debug;
    }

    /**
     * 获取微信 OpenSDK 应用 ID。
     *
     * <p>取 {@link FsUnionSDK.Config#wxAppid} 的值。
     * 用于百度等三方 SDK 初始化时透传，支持微信小程序跳转等场景。
     * 不传时返回空字符串（三方 SDK 通常按未设置处理）。</p>
     *
     * @return 微信应用 ID，未配置时返回 ""
     */
    public static String resolveWxAppid() {
        FsUnionSDK.Config config = FsUnionSDK.getGlobalConfig();
        return (config != null && config.wxAppid != null) ? config.wxAppid : "";
    }
}
