一、穿山甲SDK下载与接入
请您注册登录穿山甲后台，在后台中接入中心下载穿山甲 SDK并接入，下载具体入口如下：

在开发者平台进入“接入与测试”-“广告变现”，可下载对应SDK并查看接入文档， 点击跳转


二、工程配置
此版本SDK不适用于中国以外的安卓商店/渠道。开发者如果有海外流量对接需求，可前往Pangle官网咨询，以便获取到正确的海外专属SDK版本。

1. 注意事项
68版本起，穿山甲SDK支持minSdkVersion为24，即兼容的最小手机系统为7.0，7.0以下手机运行穿山甲SDK将会初始化和加载广告失败，minSdkVersion相关工程编译问题可以参考如下：
若开发者主项目minSdkVersion >= 24，可正常使用，忽略该项。
若开发者主项目minSdkVersion < 24，编译过程遇到 Manifest merger failed : uses-sdk:minSdkVersion xx cannot be smaller than version 24 declared in library csj相关库 /Users/xxx/AndroidManifest.xml问题，可参考以下2种解决方案，选择其一即可，也可参考官方解决。
修改主项目的最低版本minSdkVersion为24即可解决
主项目AndroidManifest.xml调整tools : overrideLibrary标签解决，如果开发者主项目清单已经有tools.overrideLibrary标签，则在已有包名后，增加穿山甲相关库名，并以逗号隔开，如果主项目清单没有该标签，则新增该标签再添加相关库名
复制
//开发者需添加库名： //com.bytedance.gromore,com.bytedance.sdk.openadsdk,com.bytedance.tools
​
​
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
​
    ...
    //如开发者之前无tools:overrideLibrary，则新建
    <uses-sdk tools:overrideLibrary="com.bytedance.gromore,com.bytedance.sdk.openadsdk,com.bytedance.tools"/>


    //如开发者之前已有tools:overrideLibrary
    <uses-sdk tools:overrideLibrary="com.aaa.bbb,com.ccc.ddd" />
    //调整后的，新增了 穿山甲相关库，并以逗号隔开
    <uses-sdk tools:overrideLibrary="com.aaa.bbb,com.ccc.ddd,com.bytedance.gromore,com.bytedance.sdk.openadsdk,com.bytedance.tools"/>
​
    ...

</manifest>
在项目的build.gradle中添加SDK下载库依赖：implementation 'com.squareup.okhttp3:okhttp:3.12.1'
2. SDK集成
我们建议您使用Gradle依赖更轻松地管理 Android Studio 项目的库依赖，而不是直接下载并安装 SDK。

版本

v7.6.1.2

MD5值

8c34b9cf2943242cf7a883690118c284

2.1 方式一：mave集成
步骤一：添加仓库

AGP版本要求：AGP版本要求>= 3.3.3 ，开发者可根据自身情况进行升级

在project级别的build.gradle文件中添加Maven的引用

复制
allprojects {
    repositories {
        maven {
          url 'https://artifact.bytedance.com/repository/pangle'
            }
      }
}
步骤二：添加依赖

在主module的build.gradle文件添加SDK依赖，sdk版本请以穿山甲平台接入中心披露的版本号为准

复制
dependencies {
     implementation 'com.pangle.cn:ads-sdk-pro:7.6.1.2'
}
2.2 方式二：手动集成
导入aar及SDK依赖的jar包：将平台下载的SDK解压后获得的open_ad_sdk.aar复制到Application Module/libs文件夹(没有的话须手动创建), 并将以下代码添加到您app的build.gradle中：

复制
repositories {
    flatDir {
        dirs 'libs'
    }
}
dependencies {
    compile(name: 'open_ad_sdk', ext: 'aar')
}
2.3 AndroidManifest配置
2.3.1 权限声明
SDK隐私协议及获取用途 请点击查看详情 link

复制
<!--必要权限-->
<uses-permission android:name="android.permission.INTERNET" />
​
<!--必要权限，解决安全风险漏洞，发送和注册广播事件需要调用带有传递权限的接口-->
<permission      android:name="${applicationId}.openadsdk.permission.TT_PANGOLIN"
        android:protectionLevel="signature" />
​
    <uses-permission android:name="${applicationId}.openadsdk.permission.TT_PANGOLIN" />

​
<!--可选权限-->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
<uses-permission android:name="android.permission.GET_TASKS"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
​
<!--可选，穿山甲提供“获取地理位置权限”和“不给予地理位置权限，开发者传入地理位置参数”两种方式上报用户位置，两种方式均可不选，添加位置权限或参数将帮助投放定位广告-->
<!--请注意：无论通过何种方式提供给穿山甲用户地理位置，均需向用户声明地理位置权限将应用于穿山甲广告投放，穿山甲不强制获取地理位置信息-->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<!--播放直播的悬浮窗，如果去掉的话直播小窗无法弹出-->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
​
<!--demo场景用到的权限，不是必须的-->
<uses-permission android:name="android.permission.RECEIVE_USER_PRESENT" />
<uses-permission android:name="android.permission.EXPAND_STATUS_BAR" />
​
<!-- 建议添加“query_all_package”权限，穿山甲将通过此权限在Android R系统上判定广告对应的应用是否在用户的app上安装，避免投放错误的广告，以此提高用户的广告体验。若添加此权限，需要在您的用户隐私文档中声明！ -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
注意: 穿山甲SDK不强制获取以上权限，即使没有获取可选权限SDK也能正常运行；建议在广告请求前合适的时机调用SDK提供的方法获取以上权限，将帮助穿山甲优化投放广告精准度和用户的交互体验，提高eCPM。

复制
//TTAdManager接口中的方法，context可以是Activity或Application
void requestPermissionIfNecessary(Context context);
2.3.2 provider配置
为不影响下载类型广告使用，无论APP处于任何阶段provider都需要在清单文件中正常配置，如果您的应用需要在Anroid7.0及以上环境运行，请在AndroidManifest中添加如下代码：
复制
<provider
    android:name="com.bytedance.sdk.openadsdk.TTFileProvider"
    android:authorities="${applicationId}.TTFileProvider"
    android:exported="false"
    android:grantUriPermissions="true">
   <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
在res/xml目录下，新建一个xml文件file_paths，在该文件中添加如下代码：

复制
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
   <!--为了适配所有路径可以设置 path = "." -->
   <external-path name="tt_external_root" path="." />
   <external-path name="tt_external_download" path="Download" />
   <external-files-path name="tt_external_files_download" path="Download" />
   <files-path name="tt_internal_file_download" path="Download" />
   <cache-path name="tt_internal_cache_download" path="Download" />
</paths>
若SDK版本<73xx版本，为不影响到广告的转化及收益，单进程或多进程请务必在清单文件中配置xxx.TTMultiProvider，73xx及以上版本则不需要配置TTMultiProvider
复制
<!-- 73xx及以上版本则无需配置TTMultiProvider -->
<provider
android:name="com.bytedance.sdk.openadsdk.multipro.TTMultiProvider"   android:authorities="${applicationId}.TTMultiProvider"
android:exported="false" />
${applicationId} 必须与开发者包名保持一致，否则会引发崩溃问题
为了适配下载和安装相关功能，在工程中引用的包 com.android.support:support-v4:28.0.0，建议使用28.0.0以及以上版本
2.3.3 运行环境配置
2.3.3.1 系统版本
广告SDK可运行于Android 7.0 (API Level 24) 及以上版本。<uses-sdk android:minSdkVersion="24" android:targetSdkVersion="30" />

如果开发者声明targetSdkVersion到API 23以上，请确保调用本SDK的任何接口前，已经申请到了SDK要求的所有权限，否则SDK部分特性可能受限

2.3.3.2 支持架构
75xx及以上：SDK默认支持arm64-v8a

75xx以下：SDK默认支持armeabi-v7a,arm64-v8a两种架构

如果有其他架构（armeabi架构）需求，请联系技术支持同学获取；



您可以在应用中的build.gradle中使用abiFilters选择支持的架构。如下所示：

复制
ndk { // 设置支持的 SO 库构架，注意这里要根据你的实际情况来设置
   abiFilters  armeabi-v7a ,  arm64-v8a
}
2.3.3.3 代码混淆
如果您需要使用proguard混淆代码，需确保不要混淆SDK的代码。以aar包里的混淆文件为准， SDK代码被混淆后会导致广告无法展现或者其它异常

2.3.3.4 资源混淆
从平台下载的SDK包中whiteList.txt 白名单上的资源不支持混淆，未成功 keep 白名单内的资源，会导致穿山甲部分资源被清理，进而出现广告无法展示、崩溃等问题。


三、初始化
注意事项

进程设置：SDK支持多进程，主进程必须初始化，其他进程若有使用到穿山甲广告业务也需要初始化(初始化包括init和start两部分)，单进程多次初始化SDK以第一次初始设置的信息为主。若项目是单进程supportMultiProcess必须更改为false；若项目是多进程，supportMultiProcess则需要设置true。多进程中如果每个进程中都需要展示广告， 则每个进程都需进行SDK的初始化操作 ；多进程涉及WebView的使用，用户想要使用自己的数据路径，可以在SDK初始化之前调用WebView.setDataDirectorySuffix()判断多进程方法：a、穿山甲sdk初始化，b、穿山甲广告获取，c、穿山甲广告展示 这三个关键点的调用在不同进程则为多进程，多进程效率不如单进程高，如非必要尽量不要使用多进程开关，
初始化配置TTAdSdk.init仅进行初始化，不会获取个人信息, 如果要展示广告，需要再调用TTAdSdk.start方法appId为必填内容(应用ID：5开头的7位数字)，若appid是通过服务端下发的，那么在初始化前需要做不为空的判断。接入过程中强烈建议双端分别使用各自的应用ID进行测试，否则可能会影响收益。allowShowNotify(true) 默认为true，即允许sdk展示通知栏提示，若设置为false则会导致通知栏不显示下载进度，存在违规风险，请勿随意更改。
provider配置无论单进程还是多进程都必须配置穿山甲所需provider无论平台应用处于测试状态还是正式状态都需要配置providerSDK版本<V73xx，为了不影响广告的转化及收益，请务必在清单文件中配xxx.TTMultiProvider
混淆配置如果您的应用对资源进行混淆（如andResGuard），请不要混淆穿山甲的任何资源，防止资源找不到而发生崩溃的问题。SDK压缩文件内whiteList.txt 白名单上的资源不支持混淆 ，开发者在SDK更新迭代过程中需要重新检查一遍白名单内容。
1. 步骤一：初始化SDK
TTAdSdk.init仅进行初始化，不会获取个人信息，需要在隐私协议后调用TTAdSdk.start方法及广告请求

复制
public class DemoApplication extends Application {
    public static String PROCESS_NAME_XXXX = "process_name_xxxx";
​
    @Override
    public void onCreate() {
        super.onCreate();
        //setp1：初始化SDK 强烈建议在应用的Application#onCreate()方法中调用，避免出现content为null的异常
        TTAdSdk.init(context,
            new TTAdConfig.Builder()
                .appId("xxxxxxx")//xxxxxxx为穿山甲媒体平台注册的应用ID(5开头的7位数字)
                .appName("APP测试媒体")//appName不为空即可，建议和平台创建的应用名称保持一致。
                .titleBarTheme(TTAdConstant.TITLE_BAR_THEME_DARK)//落地页主题
                .allowShowNotify(true) //是否允许sdk展示通知栏提示,若设置为false则会导致通知栏不显示下载进度
                .debug(true) //测试阶段打开，可以通过日志排查问题，上线时去除该调用
                .directDownloadNetworkType(TTAdConstant.NETWORK_STATE_WIFI) //允许直接下载的网络状态集合,没有设置的网络下点击下载apk会有二次确认弹窗，弹窗中会披露应用信息
                .supportMultiProcess(false) //是否支持多进程，true支持
                .updateAdConfig(ttAdConfig)//参数类型为TTAdConfig；注意使用该方法会覆盖之前初始化sdk的配置的data值；个性化推荐设置详见：https://www.csjplatform.com/supportcenter/26234
                .build());

        //如果明确某个进程不会使用到广告SDK，可以只针对特定进程初始化广告SDK的content
        //if (PROCESS_NAME_XXXX.equals(processName)) {
        //   TTAdSdk.init(context, config);
        //}

    }
}
2. 步骤二：启动SDK
复制
public void initSDK(Context context) {
    //setp2：启动SDK 需要在用户同意隐私协议后调用
       TTAdSdk.start(new TTAdSdk.Callback() {
        @Override
        public void success() {
          Log.i(TAG, "success: " + TTAdSdk.isSdkReady());
            startActivity(context);
        }
​
        @Override
        public void fail(int code, String msg) {
          Log.i(TAG, "fail:  code = " + code + " msg = " + msg);

        }
       });
}
​
3. 接口说明
3.1 初始化
穿山甲SDK初始化API必须在主线程中调用，穿山甲会将初始化操作放在子线程执行。

复制
// TTAdSdk.init返回值为 boolean
public static boolean isInit = TTAdSdk.init(context, buildConfig(context));
调用TTAdSdk.start后，可以通过TTAdSdk.isSdkReady判断初始化状态

复制
if(TTAdSdk.isSdkReady()) {
    // sdk初始化完成，可以进行广告加载等后续操作
}
初始化失败状态码

错误码

说明

4000

本地执行API错误导致初始化错误码

4201

1. SDK需要依赖 appcompat-v7 库；复制 implementation "com.android.support:appcompat-v7:28.0.0"
2.架构不匹配时会提示该错误，SDK默认支持armeabi-v7a,arm64-v8a两种架构；
3. 若为androidx项目，请检查androidx及gradle相关配置是否正确；

3.2 TTAdManager对象
TTAdManager对象为整个SDK的入口接口，可用于广告获取、权限请求、版本号获取等

复制
//一定要在初始化后才能调用，否则为空
TTAdManager ttAdManager = TTAdSdk.getAdManager();
3.2.1 接口说明
方法

返回类型

说明

createAdNative(Context context)

TTAdNative

创建广告对象TTAdNative

requestPermissionIfNecessary(Context context)

void

部分机型需要主动申请权限，如 READ_PHONE_STATE权限

tryShowInstallDialogWhenExit(Context context, final ExitInstallListener listener)

boolean

退出时尝试显示"提示安装app"对话框 ：true显示对话框；false不显示对话框

getSDKVersion()

String

获取穿山甲sdk版本号

setThemeStatus(int themeStatus)

void

设置主题类型，0：正常模式；1：夜间模式；默认为0；传非法值，按照0处理

getThemeStatus()

int

获取当前主题类型

3.3 隐私协议配置
3.3.1 使用示例
复制
private static TTAdConfig buildConfig(Context context) {
    return new TTAdConfig.Builder()
            ...
            .customController(new TTCustomController() {
                @Override
                public boolean isCanUsePhoneState() {
                    return true;
                }
                //不允许获取传感器影响较大，不建议设置
                Map<String,Object> map = new HashMap<String,Object>();
                 /**
                 支持传入"motion_info"字段表示是否允许采集传感器。 0不允许采集，1允许采集，传其他值或不实现该协议方法默认为采集
                 */
                 );
                 map.put（"motion_info",0）
                 return map;
            })
            ...
}
3.3.2 接口说明
注意事项

重写getTTLocation()之前需要设置isCanUseLocation()
重写getDevImei()之前需要先设置isCanUsePhoneState()
方法

返回类型

说明

isCanUseLocation()

boolean

是否允许SDK主动使用地理位置信息： true可以获取，false禁止获取。默认为true

getTTLocation()

TTLocation

地理位置参数，当isCanUseLocation=false时，可传入地理位置信息，穿山甲sdk使用您传入的地理位置信息

isCanUsePhoneState()

boolean

是否允许SDK主动使用手机硬件参数，如：imei
true可以使用，false禁止使用。默认为true

getDevImei()

String

当isCanUsePhoneState=false时，可传入imei信息，穿山甲sdk使用您传入的imei信息

isCanUseWifiState()

boolean

是否允许SDK主动使用ACCESS_WIFI_STATE权限
true可以使用，false禁止使用。默认为true

isCanUseWriteExternal()

boolean

是否允许SDK主动使用WRITE_EXTERNAL_STORAGE权限
true可以使用，false禁止使用。默认为true

getDevOaid()

String

开发者可以传入oaid
信通院OAID的相关采集——如何获取OAID：
1. 移动安全联盟官网http://www.msa-alliance.cn/
2. 信通院统一SDK下载http://msa-alliance.cn/col.jsp?id=120

alist()

boolean

是否允许SDK主动获取设备上应用安装列表的采集权限
true可以使用，false禁止使用。默认为true

isCanUseAndroidId()

boolean

是否允许SDK降低ANDROID_ID的采集频率
默认true 允许 ， false 不允许

isCanUsePermissionRecordAudio()

boolean

是否允许SDK在申明和授权了的情况下使用录音权限
return true 允许 false 不允许

Map<String,Object> map = new HashMap<String,Object>();
map.put（"motion_info",0）
return map;

Map

支持传入"motion_info"字段表示是否允许采集传感器。 0不允许采集，1允许采集，传其他值或不实现该协议方法默认为采集

3.4 广告配置
3.4.1 个性化广告设置
开发者可以调用接口，向用户提供退出个性化广告的能力。退出后看到的广告相关度会降低，详细使用请参考个性化广告设置

3.4.2 屏蔽未成年广告限制
开发者通过穿山甲提供的接口回传的用户年龄设置需配合穿山甲媒体平台配置使用，两者缺一不可，否则无法生效。详细使用请参考屏蔽未成年广告限制