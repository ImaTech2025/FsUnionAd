package com.ima.union.core.model;

/**
 * 策略阶段类型。
 *
 * <p>混合策略（{@link #HYBRID}）恢复为独立枚举项。<b>枚举值</b>：
 * <ul>
 *   <li>{@link #WATERFALL} — 瀑布流（按 source.priority 串行）</li>
 *   <li>{@link #BIDDING} — 实时竞价（多 source 并发）</li>
 *   <li>{@link #HYBRID} — 优先级链式（priority chain）。当 {@link AdUnitConfig#getStrategies()} 元素数
 *       {@code >= 2} 时由 {@link com.ima.union.core.strategy.HybridStrategy} 接管，返回此枚举项
 *       方便业务方打点/日志区分单阶段 vs 链式</li>
 * </ul>
 * </p>
 */
public enum StrategyType {
    WATERFALL,
    BIDDING,
    HYBRID
}
