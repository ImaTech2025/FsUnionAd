package com.ima.union.core.model;

/**
 * 聚合 SDK 统一隐私合规配置。
 *
 * <p>业务方通过 {@link com.ima.union.FsUnionSDK.Config.Builder#privacyConfig(FsUnionPrivacyConfig)}
 * 设置后，聚合内部自动将各维度开关路由到穿山甲/优量汇/百青藤/飞梭的对应 API。</p>
 *
 * <h3>默认值</h3>
 * 所有权限维度默认 {@code true}（授权），{@link #limitPersonalAds} 默认 {@code false}（不限）。
 *
 * <h3>SDK 映射关系</h3>
 * <table>
 *   <tr><th>维度</th><th>穿山甲</th><th>优量汇</th><th>百青藤</th><th>飞梭</th></tr>
 *   <tr><td>canReadDeviceId</td><td>TTCustomController</td><td>setAgreeReadDeviceId</td><td>setPermissionReadDeviceID</td><td>SensitivityController</td></tr>
 *   <tr><td>canUseLocation</td><td>TTCustomController</td><td>-</td><td>setPermissionLocation</td><td>SensitivityController</td></tr>
 *   <tr><td>canUseExternalStorage</td><td>TTCustomController</td><td>-</td><td>setPermissionStorage</td><td>-</td></tr>
 *   <tr><td>limitPersonalAds</td><td>IMediationPrivacyConfig</td><td>setPersonalizedState</td><td>setLimitPersonalAds</td><td>PERSONAL_RECOMMEND</td></tr>
 *   <tr><td>canGetApplist</td><td>alist()</td><td>-</td><td>setPermissionAppList</td><td>SensitivityController</td></tr>
 * </table>
 */
public class FsUnionPrivacyConfig {

    /** 是否授权读取设备 ID（IMEI/AndroidId/OAID 等），建议授权以提高填充率 */
    public final boolean canReadDeviceId;

    /** 是否授权读取粗略地理位置 */
    public final boolean canUseLocation;

    /** 是否授权读写外部存储 */
    public final boolean canUseExternalStorage;

    /** 是否限制个性化广告推荐（true = 退出个性化） */
    public final boolean limitPersonalAds;

    /** 是否允许采集已安装应用列表 */
    public final boolean canGetApplist;

    private FsUnionPrivacyConfig(Builder builder) {
        this.canReadDeviceId = builder.canReadDeviceId;
        this.canUseLocation = builder.canUseLocation;
        this.canUseExternalStorage = builder.canUseExternalStorage;
        this.limitPersonalAds = builder.limitPersonalAds;
        this.canGetApplist = builder.canGetApplist;
    }

    public static class Builder {
        private boolean canReadDeviceId = true;
        private boolean canUseLocation = true;
        private boolean canUseExternalStorage = true;
        private boolean limitPersonalAds = false;
        private boolean canGetApplist = true;

        /**
         * 设置是否授权读取设备 ID（默认 true，建议授权）。
         * 影响 IMEI、AndroidId、OAID 等设备标识的采集。
         */
        public Builder canReadDeviceId(boolean can) {
            this.canReadDeviceId = can;
            return this;
        }

        /**
         * 设置是否授权读取粗略地理位置（默认 true）。
         */
        public Builder canUseLocation(boolean can) {
            this.canUseLocation = can;
            return this;
        }

        /**
         * 设置是否授权读写外部存储（默认 true）。
         */
        public Builder canUseExternalStorage(boolean can) {
            this.canUseExternalStorage = can;
            return this;
        }

        /**
         * 设置是否限制个性化广告推荐（默认 false = 不限制）。
         * true 表示用户退出个性化广告，广告相关度会降低。
         */
        public Builder limitPersonalAds(boolean limit) {
            this.limitPersonalAds = limit;
            return this;
        }

        /**
         * 设置是否允许采集已安装应用列表（默认 true）。
         * 控制穿山甲 alist()、百青藤 setPermissionAppList、飞梭 setCanGetAppList。
         */
        public Builder canGetApplist(boolean can) {
            this.canGetApplist = can;
            return this;
        }

        public FsUnionPrivacyConfig build() {
            return new FsUnionPrivacyConfig(this);
        }
    }
}
