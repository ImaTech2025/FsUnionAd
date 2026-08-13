SDK集成
如果您是初次使用百度联盟SDK，我们建议您参考Demo工程来了解百度联盟SDK的使用。Demo的推荐导入方式： AndroidStudio ，请采用 AndroidStudio 2.3 以上

步骤1：添加 SDK 到工程中
方式一：Maven集成
从9.36版本开始，支持开发者使用 maven 引入百度联盟SDK

首先确保 project 级别的 build.gradle 文件中配置了官方的 MavenCentral 仓库（AndroidStudio新建项目通常默认会配置）：

allprojects {
    repositories {
        mavenCentral()
        ......
    }
}
然后在 module 级别的 build.gradle 文件中配置添加SDK对应版本的依赖（二选一）：

dependencies {
    implementation 'com.baidu:mobads:9.42.2'  // （新域名，推荐）
    implementation 'mobi.baidu.sdk:mobads:9.422'  // （旧域名，逐步淘汰）
}
方式二：aar包集成
请在工程文件根目录下创建一个名为 libs 的子目录，并将百度网盟 SDK 的 aar 包拷贝到 libs 目录下

对应 module 级别的 build.gradle 文件里面添加如下配置：

repositories {
    flatDir {
        dirs 'libs'
    }
}

dependencies {
    implementation(name:'Baidu_MobAds_SDK-release', ext:'aar')
}
注意
为适配SDK基础功能，还需要确保工程依赖了Android官方的support依赖库（支持AndroidX相关兼容库），注意版本不应低于28.0.0

dependencies {
    implementation 'com.android.support:recyclerview-v7:28.0.0'
    implementation 'com.android.support:support-v4:28.0.0'
}
步骤2: 配置AndroidManifest文件
注意： 在9.06及其以后版本中，媒体可以在AndroidManifest.xml文件中只填写权限声明部分（步骤1），无需再配置落地页AppActivity、Provider、激励视频及全屏视频部分（步骤2、3、4）。若媒体期望修改某些配置信息（如修改AppActivty的主题配置），则仍可以在AndroidManifest.xml文件中手动配置进行覆盖，如遇到合并冲突问题，可添加tools:node = "replace" 标签，详细内容请参考合并多个清单文件 (opens new window)。

9.06之前版本仍需按照以下步骤（1、2、3、4）进行配置。

步骤1. 在AndroidManifest.xml添加以下权限声明

权限说明： 百度SDK不会强制获取任何权限，以下权限为可选权限，不获取不影响SDK功能。

<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
高于Android 7的系统上，如果应用的 targetSdkVersion >= 26 ，推荐增加权限声明（SDK将通过此权限触发App安装动作）：

<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
高于Android 11的系统上，如果应用的 targetSdkVersion >= 30 ，推荐增加以下权限声明（SDK将通过此权限正常触发广告行为，并保证广告的正确投放。此权限需要在用户隐私文档中声明。）：

<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
步骤2. 在AndroidManifest.xml声明打开落地页的Activity（不建议修改主题配置，9.06以上SDK无需再单独配置）

<activity
    android:name="com.baidu.mobads.sdk.api.AppActivity"
    android:configChanges="screenSize|keyboard|keyboardHidden|orientation"
    android:theme="@android:style/Theme.NoTitleBar"/>
步骤3. 在AndroidManifest.xml声明打开显示激励视频/全屏视频的Activity（9.06以上SDK无需再单独配置）

<!-- 如果使用激励视频/全屏视频功能，需要主动在AndroidManifest.xml里面声明MobRewardVideoActivity -->
<activity
            android:name="com.baidu.mobads.sdk.api.MobRewardVideoActivity"
            android:configChanges="screenSize|orientation|keyboardHidden"
            android:launchMode="singleTask"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />
步骤4. 声明BdFileProvider 如果应用的targetSdkVersion >= 24，为了让 SDK 能够正常下载、安装 App 类广告，请按照下面的步骤做兼容性处理。（9.06以上SDK无需再单独配置）

(1) 首先在 AndroidManifest.xml 中的 Application 标签中添加 provider 标签

<provider
    android:name="com.baidu.mobads.sdk.api.BdFileProvider"
    android:authorities="${applicationId}.bd.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/bd_file_paths" />
</provider>
需要注意的是 provider 的 authorities 值为 ${applicationId}对于每一个开发者而言，这个值都是不同的，${applicationId} 在代码中和 Context.getPackageName() 值相等（即build.gradle文件中的applicationId字段），是应用的唯一 id。 例如 Demo 示例工程中的 build.gradle中的applicationId为 "com.baidu.mobads.demo.main"。

​ (2) 其次在项目结构下的 res 目录下添加一个 xml 文件夹，再新建一个 bd_file_paths.xml 的文件，文件内容如下：

<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
     <external-path name="bd_lv_path" path="/" />
     <external-files-path name="bdpath" path="bddownload/" />
     <external-path name="bdpathsd" path="bddownload/" />
     <files-path name="bd_files_path" path="bddownload/" />
     <cache-path name="bd_cache_path" path="bddownload/" />
</paths>
步骤3: 代码混淆
如果您需要使用 proguard 混淆代码，需确保不要混淆 SDK 的代码。请在 proguard-rules.pro 文件(或其他混淆文件)尾部添加如下配置：

-ignorewarnings
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
步骤4: 广告请求配置
开发者需要在用户同意APP的隐私协议之后 调用以下代码来初始化SDK：
BDAdConfig bdAdConfig = new BDAdConfig.Builder()
                    // 1、设置app名称，可选
                    .setAppName("网盟demo")
                    // 2、应用在mssp平台申请到的appsid，和包名一一对应，此处设置等同于在AndroidManifest.xml里面设置
                    .setAppsid("e866cfb0")
                    // 3、设置下载弹窗的类型和按钮动效样式，可选
                    .setDialogParams(new BDDialogParams.Builder()
                            .setDlDialogType(BDDialogParams.TYPE_BOTTOM_POPUP)
                            .setDlDialogAnimStyle(BDDialogParams.ANIM_STYLE_NONE)
                            .build())
                    // 4、控制台debug日志调试开关 接入调试阶段可以打开，上线前需关闭
                    .setDebug(false)
                    // 5、设置微信openSDK 应用id
                    .setWXAppid("")
                    // 6、如需获知SDK初始化结果，可选择性注册监听
                    .setBDAdInitListener(new BDAdConfig.BDAdInitListener() {
                        @Override
                        public void success() {
                            Log.e("MobadsApplication","SDK初始化成功");
                        }

                        @Override
                        public void fail() {
                            Log.e("MobadsApplication","SDK初始化失败");
                        }
                    })
                    .build(this);
            bdAdConfig.init();


// 设置SDK可以使用的权限，包含：设备信息、定位、存储
// 注意：建议授权SDK读取设备信息，SDK会在应用获得系统权限后自行获取IMEI等设备信息
// 授权SDK获取设备信息会有助于提升ECPM
MobadsPermissionSettings.setPermissionReadDeviceID(true);
MobadsPermissionSettings.setPermissionLocation(true);
MobadsPermissionSettings.setPermissionStorage(true);
bdAdConfig.init方法需要在用户"同意隐私协议"后方可调用，但必须要调用才可完全使用SDK功能
设置SDK可以使用的权限，包含：设备信息、定位、存储。具体规则请参考以下文档中"敏感权限"配置。
自SDK9.19版本后，无需媒体再额外依赖信通院SDK，SDK进行OAID自采集。
如果您是更新SDK，请检查相关产品API是否变更。
获取SDK版本号，可调用方法：AdSettings.getSDKVersion();
下载弹窗的样式类型及其按钮动效可在初始化时全局配置生效，目前可供选择的弹窗类型和按钮动效类型如下表：
弹窗样式类型	对应值	弹窗按钮动效类型	对应值
底部呼起	BDDialogParams.TYPE_BOTTOM_POPUP	无动效	BDDialogParams.ANIM_STYLE_NONE
居中普通	BDDialogParams.TYPE_BOTTOM_NORMAL	呼吸	BDDialogParams.ANIM_STYLE_BREATHE
居中点缀	BDDialogParams.TYPE_BOTTOM_DECORATE	扫光	BDDialogParams.ANIM_STYLE_SWIPE