package com.ima.union.utils;

import android.util.Log;

/**
 * 聚合 SDK 统一日志工具。
 *
 * <p>设计初衷：SDK 日志应受业务方控制，release 包默认关闭，避免日志泄露内部逻辑。
 * 业务方通过 {@link com.ima.union.FsUnionSDK.Config.Builder#enableLog(boolean)} 控制。</p>
 *
 * <p>用法：
 * <ul>
 *   <li>带 TAG：{@code FsLogger.d(TAG, msg)} — 日志带"/TAG"后缀，便于按类过滤</li>
 *   <li>无 TAG：{@code FsLogger.d(msg)} — 使用全局 tag "FsUnionSDK"，适配器层推荐此方式</li>
 * </ul>
 * 当 debug 关闭时自动跳过，零开销。</p>
 */
public class FsLogger {

    private static volatile boolean debug = false;
    private static String GLOBAL_TAG = "FsUnionSDK";

    private FsLogger() {}

    /** 由 FsUnionSDK.initialize() 调用，业务方也可直接调用动态开关。 */
    public static void setDebug(boolean enabled) {
        debug = enabled;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void d(String tag, String msg) {
        if (debug) Log.d(GLOBAL_TAG + "/" + tag, msg);
    }

    public static void d(String tag, String msg, Throwable tr) {
        if (debug) Log.d(GLOBAL_TAG + "/" + tag, msg, tr);
    }

    public static void i(String tag, String msg) {
        if (debug) Log.i(GLOBAL_TAG + "/" + tag, msg);
    }

    public static void i(String tag, String msg, Throwable tr) {
        if (debug) Log.i(GLOBAL_TAG + "/" + tag, msg, tr);
    }

    public static void w(String tag, String msg) {
        if (debug) Log.w(GLOBAL_TAG + "/" + tag, msg);
    }

    public static void w(String tag, String msg, Throwable tr) {
        if (debug) Log.w(GLOBAL_TAG + "/" + tag, msg, tr);
    }

    public static void e(String tag, String msg) {
        if (debug) Log.e(GLOBAL_TAG + "/" + tag, msg);
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (debug) Log.e(GLOBAL_TAG + "/" + tag, msg, tr);
    }

    // ── 无 TAG 重载（适配器层用，全局统一 tag）──

    public static void d(String msg) {
        if (debug) Log.d(GLOBAL_TAG, msg);
    }

    public static void d(String msg, Throwable tr) {
        if (debug) Log.d(GLOBAL_TAG, msg, tr);
    }

    public static void i(String msg) {
        if (debug) Log.i(GLOBAL_TAG, msg);
    }

    public static void i(String msg, Throwable tr) {
        if (debug) Log.i(GLOBAL_TAG, msg, tr);
    }

    public static void w(String msg) {
        if (debug) Log.w(GLOBAL_TAG, msg);
    }

    public static void w(String msg, Throwable tr) {
        if (debug) Log.w(GLOBAL_TAG, msg, tr);
    }

    public static void e(String msg) {
        if (debug) Log.e(GLOBAL_TAG, msg);
    }

    public static void e(String msg, Throwable tr) {
        if (debug) Log.e(GLOBAL_TAG, msg, tr);
    }
}
