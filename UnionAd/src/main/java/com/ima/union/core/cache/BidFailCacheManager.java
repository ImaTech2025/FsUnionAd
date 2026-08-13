package com.ima.union.core.cache;



import com.ima.union.utils.FsLogger;
import com.ima.union.core.model.UnionAdResponse;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 竞败缓存管理器（单例）。
 * <p>以 {@code slotId} 为 key，存储本次 bidding 中竞败（参与竞价但未拿到展示机会）的广告。</p>
 *
 * <p><b>核心场景</b>：
 * <ul>
 *   <li>{@link #addFailBid}：bidding 中加载失败的广告存入缓存</li>
 *   <li>{@link #claimBestValidBid}：下次 bidding 时用 CAS 抢令牌取出最高价且未过期的广告
 *       参与比价（防止并发同 slotId 的 bidding 重复取出同一份）</li>
 *   <li>{@link #removeExpired}：清理过期广告，避免缓存无限增长</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全</b>：内部使用 {@code ConcurrentHashMap} + {@code CopyOnWriteArrayList}，
 * 支持并发读写。</p>
 *
 * <p><b>使用注意</b>：
 * <ul>
 *   <li>取出的广告会标记 {@code fromCache=true}，以示与本次实时召回的区别</li>
 *   <li>过期判定依赖 {@link UnionAdResponse#isExpired(long)}，广告平台返回的 {@code expireTimeMs} 决定有效期</li>
 *   <li>缓存不支持持久化，进程杀死后清空</li>
 * </ul>
 * </p>
 */
public class BidFailCacheManager {

        private static final BidFailCacheManager INSTANCE = new BidFailCacheManager();

    /**
     * slotId → 竞败广告列表（按插入顺序；取用时排序拿最高价）。
     * <p>用 {@code CopyOnWriteArrayList} 保证并发写入安全；
     * 每次 {@code addFailBid} 是 append 操作，读多写少场景合适。</p>
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<UnionAdResponse>> cache = new ConcurrentHashMap<>();

    private BidFailCacheManager() {}

    public static BidFailCacheManager getInstance() {
        return INSTANCE;
    }

    // ======================== 对外 API ========================

    /**
     * 添加竞败广告到缓存。
     * <p>每次添加前会先清理该 slotId 下的过期广告，避免缓存无限增长。</p>
     *
     * @param slotId  广告位 ID
     * @param response 竞败的广告响应（实时召回的，{@code fromCache=false}）
     */
    public void addFailBid(String slotId, UnionAdResponse response) {
        if (slotId == null || response == null) {
            return;
        }
        // 先清理过期广告（避免每次 add 都遍历，只在 size 较大时清理；此处简单处理：每次 add 都清理）
        removeExpired(slotId, System.currentTimeMillis());

        CopyOnWriteArrayList<UnionAdResponse> list = cache.get(slotId);
        if (list == null) {
            cache.putIfAbsent(slotId, new CopyOnWriteArrayList<>());
            list = cache.get(slotId);
        }
        // 同 adUnitId 去重：如果已存在，保留 eCPM 更高的
        for (int i = 0; i < list.size(); i++) {
            UnionAdResponse r = list.get(i);
            if (r.getAdUnitId() != null && r.getAdUnitId().equals(response.getAdUnitId())) {
                if (response.getEcpm() > r.getEcpm()) {
                    list.set(i, response);
                    FsLogger.d("BidFailCacheManager", "BidFailCache: updated " + response.getSdkName() + " ecpm=" + response.getEcpm() + " (was " + r.getEcpm() + ") for slotId=" + slotId);
                } else {
                    FsLogger.d("BidFailCacheManager", "BidFailCache: skip lower/equal ecpm for adUnitId=" + response.getAdUnitId() + " slotId=" + slotId);
                }
                return;
            }
        }
        list.add(response);
        FsLogger.d("BidFailCacheManager", "BidFailCache: added " + response.getSdkName() + " ecpm=" + response.getEcpm() + " for slotId=" + slotId + ", total=" + list.size());
    }

    /**
     * CAS 抢令牌取出指定 slotId 的最高价且未过期的竞败广告，并从缓存中移除。
     * 
     * <p>遍历逻辑：先按 eCPM 找最高价未过期候选（不抢令牌，只读），再对候选
     * {@code tryClaim()} 抢令牌；抢到者从列表 remove 并返回，抢不到（已被并发拿走）
     * 返回 {@code null}。</p>
     * <p><b>注意</b>：本方法只做"取时过期判定"（{@code r.isExpired(now)} 跳过）。调用方拿到后
     * 仍需自行 {@code isExpired(now2)} 二次判定，因为从取到用之间有微小时间窗；
     * 如果发现已过期，应调 {@link #removeSpecificBid} 移除过期项并重试本方法拿次高价。</p>
     *
     * @param slotId 广告位 ID
     * @param now    当前时间戳（ms）
     * @return 抢到的最高价且未过期广告（标记 {@code fromCache=true}）；没有则返回 {@code null}
     */
    public UnionAdResponse claimBestValidBid(String slotId, long now) {
        if (slotId == null) {
            return null;
        }
        CopyOnWriteArrayList<UnionAdResponse> list = cache.get(slotId);
        if (list == null || list.isEmpty()) {
            return null;
        }

        // 先清理过期广告
        removeExpired(slotId, now);

        list = cache.get(slotId);
        if (list == null || list.isEmpty()) {
            return null;
        }

        // 第一遍：找最高价 eCPM 且未过期的候选（不抢令牌，只读）
        // 抢不到令牌时不遍历次高价 —— 并发场景下"次高价"未必有，并发其他 bidding
        // 可能已经持锁把次高价也抢了。让并发第二个 bidding 拿 null 是符合预期的。
        UnionAdResponse best = null;
        for (int i = 0; i < list.size(); i++) {
            UnionAdResponse r = list.get(i);
            if (r.isExpired(now)) {
                continue;
            }
            if (best == null || r.getEcpm() > best.getEcpm()) {
                best = r;
            }
        }

        if (best == null) {
            FsLogger.d("BidFailCacheManager", "BidFailCache: claimBestValidBid slotId=" + slotId + " no valid bid (all expired or empty)");
            return null;
        }

        // CAS 抢令牌：抢不到说明并发其他 bidding 已抢走
        if (!best.tryClaim()) {
            FsLogger.d("BidFailCacheManager", "BidFailCache: best bid already claimed by another bidding, slotId=" + slotId + " best=" + best.getSdkName());
            return null;
        }

        // 抢到令牌 → 从列表 remove
        list.remove(best);

        FsLogger.d("BidFailCacheManager", "BidFailCache: claimBestValidBid slotId=" + slotId + " claimed " + best.getSdkName() + " ecpm=" + best.getEcpm());
        // 返回标记 fromCache=true 的新对象；nativeAd 不信任（可能已过期），强制重新加载
        return new UnionAdResponse.Builder()
                .sdkName(best.getSdkName())
                .sdkType(best.getSdkType())
                .adFormat(best.getAdFormat())
                .adUnitId(best.getAdUnitId())
                .ecpm(best.getEcpm())
                .expireTimeMs(best.getExpireTimeMs())
                .fromCache(true)
                .nativeAd(null)
                .extra(best.getExtra())
                .build();
    }

    /**
     * 从指定 slotId 的缓存列表中移除某条具体的竞败广告。
     * <p>调用方在拿到 {@code claimBestValidBid} 返回的广告后，如果发现它已过期
     * （{@code isExpired(now2)}），应调本方法从缓存中移除该条，然后再次调
     * {@link #claimBestValidBid} 取次高价。</p>
     *
     * @param slotId   广告位 ID
     * @param response 要移除的广告（按 adUnitId 匹配）
     * @return true 表示成功移除；false 表示缓存中已无此条
     */
    public boolean removeSpecificBid(String slotId, UnionAdResponse response) {
        if (slotId == null || response == null) {
            return false;
        }
        CopyOnWriteArrayList<UnionAdResponse> list = cache.get(slotId);
        if (list == null) {
            return false;
        }
        boolean removed = list.remove(response);
        if (removed) {
            FsLogger.d("BidFailCacheManager", "BidFailCache: removeSpecificBid slotId=" + slotId + " " + response.getSdkName() + " ecpm=" + response.getEcpm());
        }
        return removed;
    }

    /**
     * 清理指定 slotId 下所有过期的广告。
     *
     * @param slotId 广告位 ID
     * @param now    当前时间戳（ms）
     */
    public void removeExpired(String slotId, long now) {
        if (slotId == null) {
            return;
        }
        CopyOnWriteArrayList<UnionAdResponse> list = cache.get(slotId);
        if (list == null) {
            return;
        }
        // 手动收集过期元素（CopyOnWriteArrayList 不支持 iterator.remove，且 removeIf 需要 Java 8 desugaring）
        UnionAdResponse[] snapshot = list.toArray(new UnionAdResponse[0]);
        int removed = 0;
        for (UnionAdResponse r : snapshot) {
            if (r.isExpired(now)) {
                if (list.remove(r)) {
                    removed++;
                }
            }
        }
        if (removed > 0) {
            FsLogger.d("BidFailCacheManager", "BidFailCache: removed " + removed + " expired bids for slotId=" + slotId);
        }
    }

    /**
     * 清除指定 slotId 的所有竞败缓存。
     * <p>切换广告位配置或测试时使用。</p>
     */
    public void clearSlot(String slotId) {
        if (slotId == null) {
            return;
        }
        CopyOnWriteArrayList<UnionAdResponse> removed = cache.remove(slotId);
        if (removed != null) {
            FsLogger.d("BidFailCacheManager", "BidFailCache: cleared slotId=" + slotId + ", removed " + removed.size() + " bids");
        }
    }

    /**
     * 清除所有竞败缓存（进程退出或 SDK 重置时调用）。
     */
    public void clearAll() {
        cache.clear();
        FsLogger.d("BidFailCacheManager", "BidFailCache: cleared all");
    }

    /**
     * 获取指定 slotId 的竞败缓存数量（调试用）。
     */
    public int getCacheSize(String slotId) {
        if (slotId == null) {
            return 0;
        }
        CopyOnWriteArrayList<UnionAdResponse> list = cache.get(slotId);
        return list != null ? list.size() : 0;
    }
}
