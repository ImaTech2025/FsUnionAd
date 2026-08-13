package com.ima.fs.unionad;

import android.app.Application;
import android.util.Log;

import com.ima.fs.unionad.custom.DemoCustomAdAdapter;
import com.ima.union.BuildConfig;
import com.ima.union.core.model.AdFormat;
import com.ima.union.FsUnionSDK;
/**
 * 聚合广告 SDK Demo Application
 *
 * <p>三方 SDK 的初始化已全部收拢到聚合 SDK 内部：
 * FsUnionSDK.initialize() 仅注册适配器，各平台 SDK 的初始化
 * 由策略引擎在执行广告请求前通过 {@code ensureInitialized()} 自动触发，
 * 根据 AdSourceConfig 中的 appId 完成初始化，无需 App 层手动调用。</p>
 */
public class FsUnionAdApp extends Application {

    private static final String TAG = "FsUnionAd";

    // ── 飞梭 Fission 测试凭证 ─────────────────────
    // APPID:  TEST
    // TOKEN:  1yh0ryt83i0czmcsugsk98q64kr7g24u
    // 测试代码位: 1_1_1 (安卓+横版图片+落地页)
    // ─────────────────────────────────────────────
    public static final String FISSION_APP_ID      = "TEST";
    public static final String FISSION_TOKEN       = "1yh0ryt83i0czmcsugsk98q64kr7g24u";
    /** 开屏 */
    public static final String FISSION_SPLASH_ID   = "1_1_1";
    /** 插屏 */
    public static final String FISSION_INTER_ID    = "1_1_1";
    /** 激励视频 */
    public static final String FISSION_REWARD_ID   = "1_1_1";
    /** 信息流模板 */
    public static final String FISSION_FEED_TP_ID  = "1_1_1";
    /** 信息流自渲染 */
    public static final String FISSION_FEED_RD_ID  = "1_1_1";

    // ── Demo 应用唯一标识 ──────────────────────────
    public static final String DEMO_APP_ID    = "fs_union_demo";


    // ── 穿山甲 Pangle 测试凭证 ────────────────────
    // APPID:  请替换为穿山甲后台分配的真实 APPID
    // SlotID: 请替换为对应格式的真实代码位 ID
    // ─────────────────────────────────────────────
    public static final String PANGLE_APP_ID      = "5001125";
    /** 开屏 */
    public static final String PANGLE_SPLASH_ID   = "887367774";
    /** 插屏（模板渲染） */
    public static final String PANGLE_INTER_ID    = "945410200";
    /** 激励视频 */
    public static final String PANGLE_REWARD_ID   = "945410199";
    /** 信息流模板 */
    public static final String PANGLE_FEED_TP_ID  = "945410198";
    /** 信息流自渲染 */
    public static final String PANGLE_FEED_RD_ID  = "945410198";

    // ── 百青藤 Baidu 测试凭证 ─────────────────────
    // APPID:  请替换为百度联盟后台分配的真实 APPID
    // SlotID: 请替换为对应格式的真实代码位 ID
    // ─────────────────────────────────────────────
    public static final String BAIDU_APP_ID       = "e866cfb0";
    /** 开屏 */
    public static final String BAIDU_SPLASH_ID    = "887367774";
    /** 插屏 */
    public static final String BAIDU_INTER_ID     = "946201351";
    /** 激励视频 */
    public static final String BAIDU_REWARD_ID    = "5925490";
    /** 信息流模板（优选模板） */
    public static final String BAIDU_FEED_TP_ID   = "945417699";
    /** 信息流自渲染 */
    public static final String BAIDU_FEED_RD_ID   = "945417699";

    private static FsUnionAdApp sInstance;

    public static FsUnionAdApp getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        // ── 1. 飞梭 TOKEN（可选：JSON 配置中已包含 FISSION token，
        //    若 assets 策略未配置 token，可在此静态注入作为兜底） ──
        // FissionAdAdapter.setFissionToken(FISSION_TOKEN);
        // ── 1. 业务方显式传入 appName + debug,不再依赖 SDK 内部硬编码/demo 名 ──
        //    appName 给 Pangle/飞梭等三方 SDK 后台展示用
        //    debug 建议绑定 BuildConfig.DEBUG,正式发版自动关闭

        // ── 2. 初始化聚合广告 SDK（注册适配器，各平台 SDK 懒初始化） ──
        //    三方 SDK 的初始化已收拢到聚合 SDK 内部：
        //    策略引擎在执行广告请求前，通过 ensureInitialized() 自动
        //    检测并完成各平台 SDK 的初始化，无需 App 层手动调用。
        FsUnionSDK.initialize(
                this,
                new FsUnionSDK.Config.Builder()
                        .appId(DEMO_APP_ID)
                        .appName(getString(R.string.app_name))   // 透传业务方应用名给三方 SDK
                        .debug(BuildConfig.DEBUG)                 // BuildConfig.DEBUG 控制三方 SDK 日志
                        .enableLog(true)
                        .build(),
                success -> {
                    Log.i(TAG, "FsUnionSDK init done");
                    LogProxy.i("Main", "FsUnionSDK 初始化完成");

                    // 3. 注册自定义 Demo Adapter（每个格式独立注册，与内置平台 key 规则一致）
                    DemoCustomAdAdapter customAdapter = new DemoCustomAdAdapter();
                    for (AdFormat fmt : AdFormat.values()) {
                        FsUnionSDK.registerCustomAdapter(customAdapter, fmt);
                    }
                    Log.i(TAG, "Custom Demo Adapter registered for all formats");
                    LogProxy.i("Main", "自定义 Demo Adapter 已注册（全部格式）");
                }
        );
    }
}
