package com.ima.union.core.model;

/**
 * 聚合链路统一竞败原因常量。
 *
 * <h3>分层设计</h3>
 * <ul>
 *   <li><b>适配器层（Adapter Level）</b>：由各 SDK 适配器在自身回调中上报，表示 SDK 层面原因</li>
 *   <li><b>链路层（Chain Level）</b>：由策略引擎（BiddingStrategy / WaterfallStrategy）在策略决策时上报，
 *       表示聚合链路层面的原因</li>
 * </ul>
 *
 * <p>所有常量通过各平台适配器的 {@code reportBidFail} 方法统一上报到对应 SDK。</p>
 */
public final class BidLossReason {

    private BidLossReason() {
        // 工具类，禁止实例化
    }

    // ════════════════════════════════════════════════════════════════
    //  适配器层（Adapter Level）—— 由适配器回调触发
    // ════════════════════════════════════════════════════════════════

    /**
     * SDK 无广告返回
     */
    public static final String NO_AD = "NO_AD";

    /**
     * SDK 加载失败
     */
    public static final String LOAD_FAILED = "LOAD_FAILED";

    /**
     * SDK 素材缓存失败
     */
    public static final String CACHE_FAILED = "CACHE_FAILED";

    /**
     * SDK 自身请求超时
     */
    public static final String TIMEOUT = "TIMEOUT";

    // ════════════════════════════════════════════════════════════════
    //  链路层（Chain Level）—— 由策略引擎触发
    // ════════════════════════════════════════════════════════════════

    /**
     * 竞价返回但 ecpm 低于其他广告源最高价
     */
    public static final String LOST_TO_HIGHER_BID = "LOST_TO_HIGHER_BID";

    /**
     * 竞价返回但 ecpm 低于底价
     */
    public static final String LOST_BELOW_FLOOR = "LOST_BELOW_FLOOR";

    /**
     * 广告已返回但未就绪（素材失效/过期）
     */
    public static final String LOST_NOT_READY = "LOST_NOT_READY";

    /**
     * 广告返回时已超竞价窗口（Bidding 宏观 timeoutMs 已到期）
     */
    public static final String BIDDING_TIMEOUT = "BIDDING_TIMEOUT";

    /**
     * 聚合链路整体超时
     */
    public static final String CHAIN_TIMEOUT = "CHAIN_TIMEOUT";

    /**
     * 聚合链路被外部取消
     */
    public static final String CHAIN_CANCELLED = "CHAIN_CANCELLED";
}
