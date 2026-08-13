# ============================================================================
# FsUnionAd 聚合广告 SDK — Consumer ProGuard Rules
# ----------------------------------------------------------------------------
# 本文件由 UnionAd 模块通过 consumerProguardFiles 声明，
# 接入方开启 minifyEnabled 后会自动合并到主工程的 ProGuard 配置中，
# 无需接入方手动拷贝任何 keep 规则。
#
# 内容分两大块：
#   A. 聚合 SDK 自身需要保留的类/接口/回调
#   B. 各联盟 SDK（穿山甲/优量汇/百青藤/飞梭）的混淆规则
# ============================================================================


# ============================================================================
# A. 聚合 SDK 自身
# ============================================================================

# ── 通用属性保留（泛型签名、注解、内部类、调试堆栈） ──────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# ── 根包：BuildConfig + UnionAdVersion + FsUnionSDK（版本号 & SDK 入口 API） ─
#    com.ima.union.*  仅匹配根包下的类，不含子包（子包有各自的 keep 规则）
-keep class com.ima.union.* { *; }

# ── 核心层：core 全包（adapter / cache / concurrent / config / model / strategy） ─
#    内含策略引擎、配置解析、线程调度、数据模型，反射/回调密集，整体保留
-keep class com.ima.union.core.** { *; }
-keep interface com.ima.union.core.** { *; }

# ── 适配器层：各平台适配器 + 自定义适配器基类 ──────────────────────────
#    接入方自定义广告源时继承 FsCustom*Adapter，基类必须保留
-keep class com.ima.union.adapters.** { *; }
-keep interface com.ima.union.adapters.** { *; }

# ── Manager 对外 API（业务方直接调用的入口） ──────────────────────────
-keep class com.ima.union.manager.** { *; }

# ── 工具类 ─────────────────────────────────────────────────────────────
-keep class com.ima.union.utils.** { *; }

# ── 数据模型：entry / impl / listener（反射序列化/回调依赖） ──────────
-keep class com.ima.union.core.model.entry.** { *; }
-keep class com.ima.union.core.model.impl.** { *; }
-keep class com.ima.union.core.model.listener.** { *; }
-keep class com.ima.union.core.model.** { *; }

# ── 回调接口（按命名模式兜底，防止漏 keep 导致回调失效） ──────────────
-keep interface com.ima.union.core.adapter.*Callback { *; }
-keep interface com.ima.union.core.adapter.*Listener { *; }


# ============================================================================
# B. 各联盟 SDK 混淆规则
# ============================================================================
# 以下规则覆盖穿山甲 / 优量汇 / 百青藤 / 飞梭四个平台。
# 接入方只需 implementation 对应 SDK，混淆 keep 自动生效。
# ----------------------------------------------------------------------------


# ── B1. 穿山甲 (Pangle / CSJ) ──────────────────────────────────────────
#    SDK 包名：com.bytedance.sdk.openadsdk / com.pangle.cn
#    适配器引用：PangleBaseAdapter 及各格式子适配器
-keep class com.bytedance.sdk.openadsdk.** { *; }
-keep class com.bytedance.sdk.account.** { *; }
-keep class com.pangle.cn.** { *; }
-dontwarn com.bytedance.sdk.openadsdk.**
-dontwarn com.bytedance.sdk.account.**
-dontwarn com.pangle.cn.**


# ── B2. 优量汇 (GDT / 腾讯广点通) ─────────────────────────────────────
#    SDK 包名：com.qq.e（ads / comm / ut / plugin 等）
#    适配器引用：GdtBaseAdapter 及各格式子适配器
-keep class com.qq.e.** { *; }
-dontwarn com.qq.e.**
# 优量汇部分版本使用 com.tencent.gdt 包
-keep class com.tencent.gdt.** { *; }
-dontwarn com.tencent.gdt.**


# ── B3. 百青藤 (Baidu / 百度联盟 MobAds) ──────────────────────────────
-dontwarn com.baidu.mobads.sdk.api.**
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.baidu.mobads.** { *; }
-keep class com.style.widget.** {*;}
-keep class com.component.** {*;}
-keep class com.baidu.ad.magic.flute.** {*;}
-keep class com.baidu.mobstat.forbes.** {*;}
#9.22版本新增加混淆，9.25版本不再要求
-keep class android.support.v7.widget.RecyclerView {*;}
-keepnames class android.support.v7.widget.RecyclerView$* {
    public <fields>;
    public <methods>;
}
-keep class android.support.v7.widget.LinearLayoutManager {*;}
-keep class android.support.v7.widget.PagerSnapHelper {*;}
-keep class android.support.v4.view.ViewCompat {*;}
-keep class android.support.v4.util.LongSparseArray {*;}
-keep class android.support.v4.util.ArraySet {*;}
-keep class android.support.v4.view.accessibility.AccessibilityNodeInfoCompat {*;}

#如果接入微信小游戏调起，需按微信要求添加以下keep
-keep class com.tencent.mm.opensdk.** {
    *;
}
-keep class com.tencent.wxop.** {
    *;
}
-keep class com.tencent.mm.sdk.** {
    *;
}


# ── B4. 飞梭 (Fission) ────────────────────────────────────────────────
#    SDK 包名：com.zm.fissionsdk / com.zm.adxsdk / com.zm.fda / com.tide
#    适配器引用：FissionBaseAdapter 及各格式子适配器
-keep class com.zm.fissionsdk.** { *; }
-keep class com.zm.adxsdk.** { *; }
-keep class com.zm.fda.** { *; }
-keep class com.tide.** { *; }
-dontwarn com.zm.fissionsdk.**
-dontwarn com.zm.adxsdk.**
-dontwarn com.zm.fda.**
-dontwarn com.tide.**
