package com.ima.fs.unionad;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 统一日志代理 — 同时写入 Logcat + 内存缓冲区供 UI 展示。
 *
 * <p>用法：
 * <pre>{@code
 *   LogProxy.d("Splash", "开始加载开屏广告...");       // Logcat 可见
 *   LogProxy.e("Splash", "开屏失败", new Exception()); // 带异常堆栈
 *   List<String> logs = LogProxy.drain("Splash");     // 拉取该 tag 日志
 *   LogProxy.clear("Splash");                         // 清空该 tag 日志
 * }</pre>
 * </p>
 */
public final class LogProxy {

    private static final int MAX_LOGS_PER_TAG = 300;
    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    // ── 入口：Logcat 可见（verbose级别，过滤用 TAG:DEMO） ──
    private static final String GLOBAL_TAG = "FsAdDemo";

    /** 按 tag 分组存放日志 */
    private static final java.util.Map<String, List<String>> sLogMap = new java.util.LinkedHashMap<>();

    private LogProxy() { /* static only */ }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /** 调试日志：同时输出到 Logcat.d + 内存 */
    public static void d(String tag, String msg) {
        String ts = "[" + SDF.format(new Date()) + "]";
        String full = ts + " " + msg;
        Log.d(GLOBAL_TAG, "[" + tag + "] " + msg);
        append(tag, full);
    }

    /** 信息日志：同时输出到 Logcat.i + 内存 */
    public static void i(String tag, String msg) {
        String ts = "[" + SDF.format(new Date()) + "]";
        String full = ts + " " + msg;
        Log.i(GLOBAL_TAG, "[" + tag + "] " + msg);
        append(tag, full);
    }

    /** 警告日志：同时输出到 Logcat.w + 内存 */
    public static void w(String tag, String msg) {
        String ts = "[" + SDF.format(new Date()) + "]";
        String full = ts + " [W] " + msg;
        Log.w(GLOBAL_TAG, "[" + tag + "] " + msg);
        append(tag, full);
    }

    /** 错误日志：同时输出到 Logcat.e + 内存（红色标记） */
    public static void e(String tag, String msg) {
        String ts = "[" + SDF.format(new Date()) + "]";
        String full = ts + " [E] " + msg;
        Log.e(GLOBAL_TAG, "[" + tag + "] " + msg);
        append(tag, full);
    }

    /** 错误日志 + 异常堆栈 */
    public static void e(String tag, String msg, Throwable tr) {
        String ts = "[" + SDF.format(new Date()) + "]";
        String full = ts + " [E] " + msg + "\n" + Log.getStackTraceString(tr);
        Log.e(GLOBAL_TAG, "[" + tag + "] " + msg, tr);
        append(tag, full);
    }

    // ─────────────────────────────────────────────────────────────
    // Log management
    // ─────────────────────────────────────────────────────────────

    /** 拉取指定 tag 的全部日志（最新在前），不删除 */
    public static List<String> getLogs(String tag) {
        synchronized (sLogMap) {
            List<String> list = sLogMap.get(tag);
            if (list == null) return Collections.emptyList();
            return new ArrayList<>(list);
        }
    }

    /** 拉取指定 tag 的全部日志并清空 */
    public static List<String> drain(String tag) {
        synchronized (sLogMap) {
            List<String> list = sLogMap.remove(tag);
            if (list == null) return Collections.emptyList();
            return new ArrayList<>(list);
        }
    }

    /** 清空指定 tag 的所有日志 */
    public static void clear(String tag) {
        synchronized (sLogMap) {
            sLogMap.remove(tag);
        }
    }

    /** 清空所有 tag 日志 */
    public static void clearAll() {
        synchronized (sLogMap) {
            sLogMap.clear();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────

    private static void append(String tag, String entry) {
        synchronized (sLogMap) {
            List<String> list = sLogMap.computeIfAbsent(tag, k -> new ArrayList<>());
            list.add(0, entry); // 最新在前
            if (list.size() > MAX_LOGS_PER_TAG) {
                list.subList(MAX_LOGS_PER_TAG, list.size()).clear();
            }
        }
    }
}
