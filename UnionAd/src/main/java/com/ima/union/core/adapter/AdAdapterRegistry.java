package com.ima.union.core.adapter;

import android.content.Context;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.utils.FsLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.ima.union.core.concurrent.ExecutorManager;

public class AdAdapterRegistry {

    

    /**
     * 同步版本最多等待该时间。
     */
    static final long INIT_TIMEOUT_MS = 10_000L;

    /**
     * 适配器注册表（唯一注册来源，含内置平台和自定义平台）。
     * <p>Key 格式统一为：{@code "{sdkType.getKey()}_{format}"}，
     * 如 "PANGLE_SPLASH"、"MYNETWORK_INTERSTITIAL"。</p>
     * <p>合并旧 {@code customAdapters} 到本 map，
     * 自定义平台与内置平台使用同一 key 规则，消除双轨查找。</p>
     */
    private final Map<String, AdAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * 已初始化的平台集合（唯一真相源）。
     * Key = {@code "{SDK_TYPE}_{APP_ID}"}，如 "PANGLE_123456"、"GDT_789"。
     * <p>各 Adapter 的 {@code isInitialized()} 统一委托到此集合，
     * 不再各自维护 {@code static volatile boolean initialized}。</p>
     */
    private final Set<String> initializedApps = new CopyOnWriteArraySet<>();

    /**
     * 等待初始化结果的监听器列表（按 key 分组）。所有读写均在 {@code key} 的
     * 内置 monitor 上同步：synchronized 块内一次性完成"取出+清空+唤醒"。
     */
    private final Map<String, List<AdInitListener>> waiters = new ConcurrentHashMap<>();

    /**
     * 共享工作线程池：替平台 SDK 跑 init 调用（不阻塞调用方线程）。
     * <p>采用 cached 池: 多个 SDK 可并发初始化, 各自独立线程。
     * keepAliveTime=15s: 闲置 15s 后线程自动回收, 兼顾复用效率与内存占用。</p>
     * <p>线程名以 {@code AdapterInit-} 前缀方便排查; 全部为 daemon 线程。</p>
     * <p>池的具体构造/参数由 {@link ExecutorManager} 统一管理,
     * 这里只通过 {@link ExecutorManager.PoolType#SDK_INIT} 引用, 不再各自 new 池。</p>
     */
    private final ExecutorService initExecutor = ExecutorManager.get(ExecutorManager.PoolType.SDK_INIT);

    /**
     * C5 修复：异步初始化超时调度器，用于 ensureInitializedAsync 的超时兜底。
     * <p>避免平台 SDK 不回调时 listener 永久滞留 waiters map 导致整链路卡死。</p>
     */
    private final ScheduledExecutorService initTimeoutScheduler =
            ExecutorManager.getScheduled(ExecutorManager.PoolType.TIMEOUT_SCHEDULE);

    private static final AdAdapterRegistry INSTANCE = new AdAdapterRegistry();

    private AdAdapterRegistry() {}

    public static AdAdapterRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册适配器（兼容旧的非拆分适配器，如 Baidu/Fission 仍为单一体）。
     * <p>Key = sdkType.name()，不包含格式后缀。</p>
     */
    public void register(AdAdapter adapter) {
        adapters.put(adapter.getSdkType().name(), adapter);
        FsLogger.i("AdAdapterRegistry", "Registered adapter: " + adapter.getSdkType().getSdkName()
                + " v" + adapter.getAdapterVersion());
    }

    /**
     * 注册格式特定适配器（拆分后使用）。
     * <p>Key = "{@code sdkType}_{@code format}"，如 "PANGLE_SPLASH"。</p>
     */
    public void register(AdAdapter adapter, AdFormat format) {
        String key = buildAdapterKey(adapter.getSdkType(), format);
        adapters.put(key, adapter);
        FsLogger.i("AdAdapterRegistry", "Registered adapter: " + key + " v" + adapter.getAdapterVersion());
    }

    // ── 旧接口代理，同时注册为完整集合（兼容未拆分的 Baidu/Fission） ──
    // 暂时保留 register(AdAdapter) 作为向后兼容，后续 Baidu/Fission 拆分后移除。

    /**
     * 注册自定义适配器（格式特定）。
     *
     * <p>Key 规则与内置平台完全一致：{@code "{sdkType.getKey()}_{format}"}，
     * 如 {@code CUSTOM_SPLASH}、{@code MYNETWORK_INTERSTITIAL}。</p>
     *
     * <p>策略 JSON 中某广告源的 {@code sdkType}+{@code adFormat} 与此 key 匹配时，
     * 该适配器将被用于处理该广告源的请求。</p>
     *
     * @param adapter 自定义适配器实例（sdkType 应通过 AdSdkType.of() 创建的自定义类型）
     * @param format  接入的广告格式（如 SPLASH），用于构建注册 key
     */
    public void registerCustomAdapter(AdAdapter adapter, AdFormat format) {
        // 检查：内置平台适配器不应通过此方法注册
        if (adapter.getSdkType().isBuiltIn()) {
            FsLogger.w("AdAdapterRegistry", "Custom adapter must use a type created via AdSdkType.of(), got built-in: " + adapter.getSdkType());
        }
        String key = buildAdapterKey(adapter.getSdkType(), format);
        adapters.put(key, adapter);
        FsLogger.i("AdAdapterRegistry", "Registered custom adapter: key=" + key
                + " sdkName=" + adapter.getSdkName()
                + " version=" + adapter.getAdapterVersion());
    }

    /**
     * 根据 SDK 类型查找适配器（兼容旧接口，不指定格式）。
     * <p>仅内置平台（PANGLE/GDT/BAIDU/FISSION）通过非格式 key 查找。</p>
     */
    public AdAdapter getAdapter(AdSdkType sdkType) {
        if (sdkType.isCustom()) return null;
        return adapters.get(sdkType.name());
    }

    /**
     * 根据 SDK 类型 + 广告格式查找适配器（统一入口，覆盖内置平台和自定义平台）。
     *
     * <p>查找顺序：
     * <ol>
     *   <li>统一组合键 {@code "{sdkType.getKey()}_{format}"}
     *       （内置已拆分适配器 和 自定义格式适配器 均在此路径命中）</li>
     *   <li>回退 {@code sdkType.name()}（仅内置平台未拆分的旧适配器）</li>
     * </ol>
     * </p>
     *
     * <p>合并自定义适配器查找，不再分两条路径。</p>
     */
    public AdAdapter getAdapter(AdSdkType sdkType, AdFormat format) {
        // 优先按格式查找（已拆分适配器 + 自定义格式适配器）
        String compositeKey = buildAdapterKey(sdkType, format);
        AdAdapter adapter = adapters.get(compositeKey);
        if (adapter != null) return adapter;
        // 回退：仅内置平台未拆分旧适配器
        if (sdkType.isBuiltIn()) {
            return adapters.get(sdkType.name());
        }
        return null;
    }

    public List<AdAdapter> getAllAdapters() {
        return new ArrayList<>(adapters.values());
    }

    public List<AdAdapter> getCustomAdapters() {
        List<AdAdapter> list = new ArrayList<>();
        for (AdAdapter a : adapters.values()) {
            if (a.getSdkType().isCustom()) {
                list.add(a);
            }
        }
        return list;
    }

    // ============================================================
    //  同步版本（向后兼容，初始化成功/失败均不通知）
    // ============================================================

    /**
     * 同步阻塞版本的初始化（最多等待 {@link #INIT_TIMEOUT_MS} 毫秒）。
     * <p>仅供希望"先初始化完再继续后续逻辑"的场景使用；策略层应优先使用
     * {@link #ensureInitializedAsync} 异步版本，避免长链路请求被初始化阻塞。</p>
     */
    public void ensureInitialized(Context context, AdAdapter adapter, String appId, String token) {
        final CountDownLatch latch = new CountDownLatch(1);
        ensureInitializedAsync(context, adapter, appId, token, new AdInitListener() {
            @Override
            public void onInitSuccess(AdAdapter a) { latch.countDown(); }
            @Override
            public void onInitFailure(AdAdapter a, int code, String msg) { latch.countDown(); }
        });
        try {
            if (!latch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                FsLogger.w("AdAdapterRegistry", "Init (sync) timeout for " + platformNameOf(adapter) + " (" + buildKey(adapter, appId) + ")");
            }
        } catch (InterruptedException e) {
            FsLogger.w("AdAdapterRegistry", "Init (sync) interrupted for " + platformNameOf(adapter) + " (" + buildKey(adapter, appId) + ")");
            Thread.currentThread().interrupt();
        }
    }

    // ============================================================
    //  异步版本（推荐）— 把初始化结果主动通知给调用方
    // ============================================================

    /**
     * 异步确保指定适配器已完成初始化，结果通过 {@code listener} 回调通知调用方。
     * <p>语义：</p>
     * <ul>
     *   <li>若该 {@code {SDK+AppId}} 已初始化成功 → 立即在 <b>调用方当前线程</b>回调 {@code onInitSuccess}</li>
     *   <li>若该 key 正在初始化中（其它调用方已经发起） → 将 listener 注册到等待列表，由 init 线程统一唤醒</li>
     *   <li>否则 → 投递到 {@link #initExecutor} 执行适配器初始化，完成后回调 listener</li>
     * </ul>
     * <p><b>并发安全（双重检查 + 锁内不回调）：</b></p>
     * <ul>
     *   <li>fast-path 锁外快速检查（绝大多数情况命中，不进 monitor）</li>
     *   <li>慢路径在 {@code key} 的 monitor 内做状态判定 + waiters 操作，<b>不调任何业务回调</b>，
     *       避免持锁做重活阻塞其他 key 初始化或反向重入死锁</li>
     *   <li>慢路径在锁内再做一次"已初始化"二次检查——避免 fast-path 看到 false、抢锁期间被别的
     *       {@link #deliver} 线程 add 完、但我们又抢到启动权重复 init 的窗口</li>
     *   <li>{@link #initializedApps#add} 与 {@link #waiters#remove} 均在 {@code key} 的 monitor 内，
     *       T2 deliver 释放锁时 T3 ensure 拿锁必能看到"已初始化"或"waiters 还在"两者之一，
     *       杜绝 listener 落空</li>
     * </ul>
     *
     * @param context  上下文（仅用于初始化阶段，建议传 Application 避免泄漏）
     * @param adapter  广告适配器
     * @param appId    平台 appId
     * @param token    平台 token（可为 null，部分平台如 Fission 需要）
     * @param listener 初始化结果回调（不可为 null）
     */
    public void ensureInitializedAsync(Context context, AdAdapter adapter, String appId,
                                       String token, AdInitListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("AdInitListener must not be null");
        }
        final String key = buildKey(adapter, appId);
        final String platformName = platformNameOf(adapter);

        // 0. fast-path：锁外快速检查（绝大多数情况命中，不进 monitor）
        if (initializedApps.contains(key)) {
            listener.onInitSuccess(adapter);
            return;
        }

        // 1. 慢路径：锁内只做状态决策，**绝不调业务回调**
        // 标志位语义由 PostUnlockAction 枚举定义
        PostUnlockAction action = PostUnlockAction.LAUNCH_INIT;
        synchronized (key) {
            // 1a. 二次检查：抢锁期间可能已被另一线程 deliver 完成 add
            if (initializedApps.contains(key)) {
                action = PostUnlockAction.INIT_AGAIN;
            } else {
                List<AdInitListener> existing = waiters.get(key);
                if (existing != null) {
                    // 1b. 有其他线程在初始化 → 加入 waiters 后锁内直接返回
                    //     （无需锁外动作，等 init 线程 deliver 时统一回调）
                    existing.add(listener);
                    FsLogger.d("AdAdapterRegistry", "Init already in progress for " + platformName
                            + " (" + key + "), listener joined the wait list (size="
                            + existing.size() + ")");
                    return;
                }
                // 1c. 抢到启动权 → 创建 waiters
                List<AdInitListener> newWaiters = new ArrayList<>();
                newWaiters.add(listener);
                waiters.put(key, newWaiters);
            }
        }

        // 2. 锁外分派（此时不在 monitor 内，回调安全）
        if (action == PostUnlockAction.INIT_AGAIN) {
            // 二次检查命中：抢锁期间被别的 deliver 完成
            listener.onInitSuccess(adapter);
            return;
        }
        // LAUNCH_INIT：抢到启动权 → 投递 init 任务
        final Context appContext = context.getApplicationContext();
        initExecutor.execute(() -> {
            // C5 修复：异步初始化超时兜底。平台 SDK 不回调时，到点模拟 init failure，
            // 避免 listener 永久滞留 waiters map 导致瀑布流/竞价链路永久卡死。
            // deliver 内部 synchronized(key) + waiters.remove 保证 only once：
            // 超时任务与 SDK 回调即使并发调用 deliver，也只回调 listener 一次。
            final ScheduledFuture<?> initTimeoutFuture = initTimeoutScheduler.schedule(() -> {
                FsLogger.w("AdAdapterRegistry", platformName + " init timeout ("
                        + INIT_TIMEOUT_MS + "ms), deliver failure (" + key + ")");
                deliver(key, platformName, adapter, false, 0,
                        "init timeout (" + INIT_TIMEOUT_MS + "ms)");
            }, INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            adapter.initialize(appContext, appId, token, new AdInitCallback() {
                @Override
                public void onInitSuccess() {
                    initTimeoutFuture.cancel(false);
                    deliver(key, platformName, adapter, true, 0, null);
                    FsLogger.i("AdAdapterRegistry", platformName + " initialized successfully (" + key + ")");
                }

                @Override
                public void onInitFailure(int errorCode, String errorMsg) {
                    initTimeoutFuture.cancel(false);
                    deliver(key, platformName, adapter, false, errorCode, errorMsg);
                    FsLogger.e("AdAdapterRegistry", platformName + " init failed: [" + errorCode + "] " + errorMsg);
                }
            });
        });
    }

    /**
     * 把初始化结果派发给所有等待方。
     * <p><b>锁内不回调（与 {@link #ensureInitializedAsync} 保持一致）：</b>
     * {@code key} 的 monitor 内只做"add → remove → 取出列表"三件事，
     * listener 业务回调延后到锁外遍历通知。</p>
     * <p><b>add 必须先于 remove：</b>
     * T2 deliver 持锁期间 add 完毕，T3 ensure 拿锁时二次检查必能看到"已初始化"，
     * 杜绝"既不在 waiters 又不在 initializedApps"的 listener 落空窗口。</p>
     */
    private void deliver(String key, String platformName, AdAdapter adapter,
                         boolean success, int errorCode, String errorMsg) {
        List<AdInitListener> toNotify;
        synchronized (key) {
            // 顺序关键：先 add，再 remove（让后续 ensure 的二次检查能看到 add 结果）
            if (success) {
                initializedApps.add(key);
            }
            toNotify = waiters.remove(key);
        }
        // 锁外回调业务 listener（避免持锁做重活）
        if (toNotify == null) {
            return;
        }
        if (success) {
            notifyInitSuccess(toNotify, adapter, key);
        } else {
            notifyInitFailure(toNotify, adapter, errorCode, errorMsg, key);
        }
    }

    /** 把成功结果批量派发给等待中的 listener。锁外调用。 */
    private void notifyInitSuccess(List<AdInitListener> listeners, AdAdapter adapter, String key) {
        for (AdInitListener l : listeners) {
            try {
                l.onInitSuccess(adapter);
            } catch (Throwable t) {
                FsLogger.e("AdAdapterRegistry", "AdInitListener.onInitSuccess threw for " + key, t);
            }
        }
    }

    /** 把失败结果批量派发给等待中的 listener。锁外调用。 */
    private void notifyInitFailure(List<AdInitListener> listeners, AdAdapter adapter,
                                   int errorCode, String errorMsg, String key) {
        for (AdInitListener l : listeners) {
            try {
                l.onInitFailure(adapter, errorCode, errorMsg);
            } catch (Throwable t) {
                FsLogger.e("AdAdapterRegistry", "AdInitListener.onInitFailure threw for " + key, t);
            }
        }
    }

    /**
     * ensureInitializedAsync 锁内决策后，锁外需要做的分派动作。
     * JOIN_WAITERS 路径已在锁内直接 return，不出现在这里。
     */
    private enum PostUnlockAction {
        /** 抢锁期间被别的线程 deliver 完成 add，本次锁外直接回调成功 */
        INIT_AGAIN,
        /** 抢到启动权，锁外投递 init 任务 */
        LAUNCH_INIT
    }

    /** 构建适配器组合键：{sdkType}_{format} */
    static String buildAdapterKey(AdSdkType sdkType, AdFormat format) {
        return sdkType.name() + "_" + (format != null ? format.name() : "GENERIC");
    }

    /**
     * 统一的初始化状态标记入口——唯一真相源。
     * <p>替换了旧的双轨制（{@link AdAdapter#isInitialized()} + {@link #initializedApps}），
     * 现在所有初始化状态查询都走这里。Key 格式：{@code "{SDK_TYPE}_{APP_ID}"}。</p>
     */
    public boolean isInited(AdSdkType sdkType, String appId) {
        if (appId == null) return false;
        return initializedApps.contains(buildKey(sdkType, appId));
    }

    /** 已初始化的平台数量（去重不计分格式适配器） */
    public int getInitializedPlatformCount() {
        return initializedApps.size();
    }

    private static String buildKey(AdAdapter adapter, String appId) {
        return buildKey(adapter.getSdkType(), appId);
    }

    private static String buildKey(AdSdkType sdkType, String appId) {
        return sdkType.getKey() + "_" + appId;
    }

    private static String platformNameOf(AdAdapter adapter) {
        return adapter.getSdkType().getSdkName();
    }

    public void clear() {
        adapters.clear();
        initializedApps.clear();
        waiters.clear();
    }
}
