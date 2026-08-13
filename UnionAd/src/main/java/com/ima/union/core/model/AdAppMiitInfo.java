package com.ima.union.core.model;

/**
 * 工信部合规六要素 — 应用下载类自渲染广告的应用基本信息。
 *
 * <p>各平台 SDK 获取方式：
 * <ul>
 *   <li>穿山甲：{@code TTFeedAd.getComplianceInfo()}</li>
 *   <li>优量汇：{@code NativeUnifiedADAppMiitInfo}</li>
 *   <li>百青藤：{@code NativeResponse.getPublisher/getAppVersion/getAppPrivacyLink/...}</li>
 *   <li>飞梭：{@code IFissionNative.getAppName/getDeveloperName/getAppVersion/...}</li>
 * </ul>
 *
 * <p>非应用下载类广告（如品牌广告）各字段可能为 null，调用方需判空。</p>
 */
public class AdAppMiitInfo {

    /** 应用名称 */
    private final String appName;

    /** 开发者/发布者名称 */
    private final String developerName;

    /** 应用版本号（字符串，格式由广告平台定义） */
    private final String appVersion;

    /** 隐私政策链接 */
    private final String privacyUrl;

    /** 权限列表链接 */
    private final String permissionUrl;

    /** 功能描述链接 */
    private final String functionDescUrl;

    // ==================== constructor ====================

    private AdAppMiitInfo(Builder builder) {
        this.appName = builder.appName;
        this.developerName = builder.developerName;
        this.appVersion = builder.appVersion;
        this.privacyUrl = builder.privacyUrl;
        this.permissionUrl = builder.permissionUrl;
        this.functionDescUrl = builder.functionDescUrl;
    }

    // ==================== getters ====================

    public String getAppName() { return appName; }
    public String getDeveloperName() { return developerName; }
    public String getAppVersion() { return appVersion; }
    public String getPrivacyUrl() { return privacyUrl; }
    public String getPermissionUrl() { return permissionUrl; }
    public String getFunctionDescUrl() { return functionDescUrl; }

    // ==================== builder ====================

    public static class Builder {
        private String appName;
        private String developerName;
        private String appVersion;
        private String privacyUrl;
        private String permissionUrl;
        private String functionDescUrl;

        public Builder appName(String appName) { this.appName = appName; return this; }
        public Builder developerName(String developerName) { this.developerName = developerName; return this; }
        public Builder appVersion(String appVersion) { this.appVersion = appVersion; return this; }
        public Builder privacyUrl(String privacyUrl) { this.privacyUrl = privacyUrl; return this; }
        public Builder permissionUrl(String permissionUrl) { this.permissionUrl = permissionUrl; return this; }
        public Builder functionDescUrl(String functionDescUrl) { this.functionDescUrl = functionDescUrl; return this; }

        public AdAppMiitInfo build() {
            return new AdAppMiitInfo(this);
        }
    }
}
