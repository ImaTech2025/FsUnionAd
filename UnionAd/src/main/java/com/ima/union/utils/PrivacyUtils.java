package com.ima.union.utils;

import com.ima.union.FsUnionSDK;
import com.ima.union.core.model.FsUnionPrivacyConfig;

/**
 * 隐私合规配置路由工具。
 *
 * <p>从 {@link FsUnionSDK.Config#privacyConfig} 中读取统一配置，
 * 并路由到各三方 SDK 的对应 API。各 BaseAdapter 初始化时调用对应方法。</p>
 */
public final class PrivacyUtils {

    private PrivacyUtils() {}

    /**
     * 获取当前隐私配置。如果未设置，返回默认值（全部授权，不限个性化）。
     */
    private static FsUnionPrivacyConfig get() {
        FsUnionSDK.Config config = FsUnionSDK.getGlobalConfig();
        if (config != null && config.privacyConfig != null) {
            return config.privacyConfig;
        }
        // 兜底默认配置
        return new FsUnionPrivacyConfig.Builder().build();
    }

    // ════════════════════════════════════════════════════════════════
    //  百青藤（Baidu MobAds）
    //  API: MobadsPermissionSettings.setPermissionReadDeviceID / setPermissionLocation
    //       / setPermissionStorage / setLimitPersonalAds
    // ════════════════════════════════════════════════════════════════

    /**
     * 将隐私配置应用到百青藤 SDK。
     * 在 {@code BaiduBaseAdapter.initialize()} 中调用。
     */
    public static void applyBaiduPrivacy() {
        try {
            FsUnionPrivacyConfig cfg = get();
            com.baidu.mobads.sdk.api.MobadsPermissionSettings.setPermissionReadDeviceID(cfg.canReadDeviceId);
            com.baidu.mobads.sdk.api.MobadsPermissionSettings.setPermissionLocation(cfg.canUseLocation);
            com.baidu.mobads.sdk.api.MobadsPermissionSettings.setPermissionStorage(cfg.canUseExternalStorage);
            com.baidu.mobads.sdk.api.MobadsPermissionSettings.setLimitPersonalAds(cfg.limitPersonalAds);
            com.baidu.mobads.sdk.api.MobadsPermissionSettings.setPermissionAppList(cfg.canGetApplist);
            FsLogger.d("PrivacyUtils: Baidu privacy applied"
                    + " deviceId=" + cfg.canReadDeviceId
                    + " location=" + cfg.canUseLocation
                    + " storage=" + cfg.canUseExternalStorage
                    + " limitPersonal=" + cfg.limitPersonalAds
                    + " appList=" + cfg.canGetApplist);
        } catch (Exception e) {
            FsLogger.w("PrivacyUtils: failed to apply Baidu privacy: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  优量汇（GDT）
    //  API: GlobalSetting.setAgreePrivacyStrategy / setAgreeReadDeviceId
    //       / setPersonalizedState
    // ════════════════════════════════════════════════════════════════

    /**
     * 将隐私配置应用到优量汇 SDK。
     * 在 {@code GdtBaseAdapter.initialize()} 中调用。
     */
    public static void applyGdtPrivacy() {
        try {
            FsUnionPrivacyConfig cfg = get();
            com.qq.e.comm.managers.setting.GlobalSetting.setAgreePrivacyStrategy(true);
            com.qq.e.comm.managers.setting.GlobalSetting.setAgreeReadDeviceId(cfg.canReadDeviceId);
            // GDT setPersonalizedState: 1=不限个性化, 0=限制个性化
            com.qq.e.comm.managers.setting.GlobalSetting.setPersonalizedState(cfg.limitPersonalAds ? 0 : 1);
            FsLogger.d("PrivacyUtils: GDT privacy applied"
                    + " deviceId=" + cfg.canReadDeviceId
                    + " limitPersonal=" + cfg.limitPersonalAds);
        } catch (Exception e) {
            FsLogger.w("PrivacyUtils: failed to apply GDT privacy: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  穿山甲（Pangle）
    //  API: TTAdConfig.Builder.customController(TTCustomController)
    //       TTCustomController: isCanUseAndroidId / isCanUsePhoneState
    //       / isCanUseLocation / isCanUseWriteExternal / alist
    //       getMediationPrivacyConfig().isLimitPersonalAds()
    // ════════════════════════════════════════════════════════════════

    /**
     * 创建穿山甲 TTCustomController，将隐私配置适配为穿山甲接口。
     * 在 {@code PangleBaseAdapter.buildPangleConfig()} 中调用
     * {@code .customController(PrivacyUtils.createPangleCustomController())}。
     */
    public static com.bytedance.sdk.openadsdk.TTCustomController createPangleCustomController() {
        final FsUnionPrivacyConfig cfg = get();
        return new com.bytedance.sdk.openadsdk.TTCustomController() {

            @Override public boolean isCanUseLocation() {
                return cfg.canUseLocation;
            }

            @Override public boolean isCanUsePhoneState() {
                return cfg.canReadDeviceId;
            }

            @Override public boolean isCanUseAndroidId() {
                return cfg.canReadDeviceId;
            }

            @Override public boolean isCanUseWriteExternal() {
                return cfg.canUseExternalStorage;
            }

            // alist() 在穿山甲混淆后对应 canUseAppList
            @Override public boolean alist() {
                return cfg.canGetApplist;
            }

            @Override
            public com.bytedance.sdk.openadsdk.mediation.init.IMediationPrivacyConfig getMediationPrivacyConfig() {
                return new com.bytedance.sdk.openadsdk.mediation.init.IMediationPrivacyConfig() {
                    @Override public boolean isLimitPersonalAds() {
                        return cfg.limitPersonalAds;
                    }
                    @Override public boolean isProgrammaticRecommend() {
                        return !cfg.limitPersonalAds;
                    }
                    @Override public boolean isCanUseOaid() {
                        return cfg.canReadDeviceId;
                    }
                    @Override public java.util.List<String> getCustomDevImeis() {
                        return null;
                    }
                    @Override public java.util.List<String> getCustomAppList() {
                        return null;
                    }
                };
            }
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  飞梭（Fission）
    //  API: FissionSensitivityController + FissionConfig.PERSONAL_RECOMMEND
    // ════════════════════════════════════════════════════════════════

    /**
     * 创建飞梭 FissionSensitivityController，将隐私配置适配。
     * 在 {@code FissionBaseAdapter.initialize()} 中使用。
     */
    public static com.zm.fissionsdk.api.FissionSensitivityController createFissionSensitivityController() {
        FsUnionPrivacyConfig cfg = get();
        return new com.zm.fissionsdk.api.FissionSensitivityController.Builder()
                .setCanGetAndroidId(cfg.canReadDeviceId)
                .setCanGetOaid(cfg.canReadDeviceId)
                .setCanReadPhoneState(cfg.canReadDeviceId)
                .setCanGetLocation(cfg.canUseLocation)
                .setCanGetNetworkState(true)
                .setCanGetAppList(cfg.canGetApplist)
                .build();
    }

    /**
     * 飞梭个性化推荐开关值。
     * 在 {@code FissionConfig.Builder.addGlobalConfig(FissionConfig.PERSONAL_RECOMMEND, ...)} 中使用。
     */
    public static boolean getFissionPersonalRecommend() {
        return !get().limitPersonalAds;
    }
}
