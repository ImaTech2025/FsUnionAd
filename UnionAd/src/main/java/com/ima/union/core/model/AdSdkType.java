package com.ima.union.core.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 广告平台类型（值类型，非枚举）。
 *
 * <p> * <ul>
 *   <li>4 个内置常量（{@link #PANGLE}/{@link #GDT}/{@link #BAIDU}/{@link #FISSION}）
 *       维持 {@code ==} 兼容（单例，不可变）</li>
 *   <li>{@link #of(String, String)} 工厂方法允许外部接入方创建自定义平台类型，
 *       支持媒体接入多个自定义广告源，各自拥有独立标识</li>
 *   <li>{@link #fromKey(String)} 替代旧 {@code valueOf()}，用于 JSON 反序列化时
 *       按 {@code key} 查表；未命中返回 {@code null}</li>
 *   <li>{@link #name()} 返回 {@code key}，向后兼容旧代码中 {@code sdkType.name()} 拼接 key 的用法</li>
 *   <li>{@link #getKey()} 作为注册表 key 的构成部分（替代旧 {@code name()}）</li>
 *   <li>{@link #getSdkName()} 返回平台显示名（如 "穿山甲"），用作日志/对外展示</li>
 * </ul>
 *
 * <p><b>线程安全</b>：内置常量和工厂创建的实例均为不可变对象；内部注册表使用 {@link ConcurrentHashMap}。</p>
 */
public final class AdSdkType {

    // ═══ 内置常量（== 兼容） ═══

    public static final AdSdkType PANGLE  = new AdSdkType("PANGLE",  "穿山甲");
    public static final AdSdkType GDT     = new AdSdkType("GDT",     "优量汇");
    public static final AdSdkType BAIDU   = new AdSdkType("BAIDU",   "百青藤");
    public static final AdSdkType FISSION = new AdSdkType("FISSION", "飞梭");

    // ═══ 内置 key 白名单（防外部创建时撞 key） ═══

    private static final Set<String> BUILT_IN_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("PANGLE", "GDT", "BAIDU", "FISSION")));

    // ═══ 全局注册表（线程安全） ═══

    private static final ConcurrentMap<String, AdSdkType> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (AdSdkType t : new AdSdkType[]{PANGLE, GDT, BAIDU, FISSION}) {
            REGISTRY.put(t.key, t);
        }
    }

    // ═══ 实例字段 ═══

    private final String key;       // 唯一标识，替代旧 enum.name()，如 "PANGLE"
    private final String sdkName;   // 显示名，如 "穿山甲"

    private AdSdkType(String key, String sdkName) {
        this.key = key;
        this.sdkName = sdkName;
    }

    // ═══ 工厂方法（外部自定义平台） ═══

    /**
     * 创建一个自定义平台类型。
     *
     * <p>要求：</p>
     * <ul>
     *   <li>{@code key} 不能为 null 或空字符串</li>
     *   <li>{@code key} 不能与内置 key（PANGLE/GDT/BAIDU/FISSION）冲突</li>
     *   <li>{@code key} 不能与已注册的自定义 key 重复（重复时返回已存在的实例）</li>
     * </ul>
     *
     * @param key     唯一标识（大写英文字母或数字，建议不超过 32 字符）
     * @param sdkName 平台显示名（如 "我的广告平台"）
     * @return 新创建或已存在的 AdSdkType 实例
     * @throws IllegalArgumentException key 为 null/空或与内置 key 冲突
     */
    public static AdSdkType of(String key, String sdkName) {
        Objects.requireNonNull(key, "key must not be null");
        if (key.isEmpty()) throw new IllegalArgumentException("key must not be empty");
        if (BUILT_IN_KEYS.contains(key)) {
            throw new IllegalArgumentException("key '" + key + "' is reserved");
        }
        // 兼容 Android 低版本（API 21-23）：
        // ConcurrentHashMap.computeIfAbsent() 在低版本 Android 有死锁 bug，
        // 改用 get + putIfAbsent 两步，线程安全且全版本兼容。
        AdSdkType existing = REGISTRY.get(key);
        if (existing != null) return existing;
        AdSdkType newly = new AdSdkType(key, sdkName);
        existing = REGISTRY.putIfAbsent(key, newly);
        return existing != null ? existing : newly;
    }

    // ═══ 查询方法 ═══

    /**
     * 按 {@code key} 查表，替代旧 {@code AdSdkType.valueOf(String)}。
     *
     * <p>查找顺序：先查内置常量，再查已注册的自定义类型。</p>
     *
     * @param key 唯一标识（如 "PANGLE"）
     * @return 对应的 AdSdkType 实例，未命中返回 {@code null}
     */
    public static AdSdkType fromKey(String key) {
        if (key == null || key.isEmpty()) return null;
        return REGISTRY.get(key);
    }

    /**
     * 返回唯一标识（替代旧 {@code enum.name()}）。
     * <p>向后兼容：旧代码中 {@code sdkType.name()} 的调用等价于 {@code sdkType.getKey()}。</p>
     */
    public String name() {
        return key;
    }

    // ═══ Getters ═══

    public String getKey() {
        return key;
    }

    public String getSdkName() {
        return sdkName;
    }

    // ═══ 辅助方法 ═══

    /**
     * 是否为 4 个内置常量之一（PANGLE/GDT/BAIDU/FISSION）。
     */
    public boolean isBuiltIn() {
        return this == PANGLE || this == GDT || this == BAIDU
                || this == FISSION;
    }

    /**
     * 是否为通过 {@link #of(String, String)} 创建的自定义平台类型。
     * <p>等价于 {@code !isBuiltIn()}。</p>
     */
    public boolean isCustom() {
        return !isBuiltIn();
    }

    // ═══ equals / hashCode / toString ═══

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdSdkType)) return false;
        return key.equals(((AdSdkType) o).key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key;
    }
}
