package com.ima.union.core.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.ima.union.core.concurrent.ExecutorManager;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.AdUnitConfig;
import com.ima.union.utils.FsLogger;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 云策略配置管理器（单 slotId 维度）。
 * <p>职责：</p>
 * <ul>
 *   <li>按 slotId 从云端拉取策略配置（HTTP GET）</li>
 *   <li>按 slotId 本地 SharedPreferences 缓存 + 内存缓存</li>
 *   <li>按 slotId 提供合并后的 {@link AdUnitConfig}</li>
 * </ul>
 * <p>选择优先级（高 → 低）：</p>
 * <ol>
 *   <li>本地缓存的云端配置</li>
 *   <li>请求时传入的 defaultStrategyJson（默认策略，最低优先级）</li>
 * </ol>
 *
 * 
 */
public class CloudStrategyManager {

    
    private static final String PREFS_NAME = "fs_union_cloud_config";
    private static final String KEY_CLOUD_CONFIG_FMT = "cloud_config_%s";
    private static final String KEY_CLOUD_VERSION_FMT = "cloud_version_%s";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;

    private static CloudStrategyManager INSTANCE;

    private final ExecutorService executor = ExecutorManager.get(ExecutorManager.PoolType.CONFIG_FETCH);

    // 按 slotId 隔离的内存缓存（每条配置只对应一个 slotId）
    private final Map<String, AdUnitConfig> slotConfigMap = new ConcurrentHashMap<>();
    // 按 slotId 记录是否正在拉取
    private final Map<String, Boolean> slotFetchInProgress = new ConcurrentHashMap<>();

    private Context appContext;
    private String configUrl;

    private CloudStrategyManager() {
    }

    public static synchronized CloudStrategyManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CloudStrategyManager();
        }
        return INSTANCE;
    }

    /**
     * 初始化配置管理器。
     *
     * @param context   Application Context
     * @param configUrl 云端配置 URL（可为空，表示不拉取云端）
     */
    public void initialize(Context context, String configUrl) {
        this.appContext = context.getApplicationContext();
        this.configUrl = configUrl;
    }

    /**
     * 获取广告位配置。
     * <p>选择优先级：云端（本地缓存）配置 > defaultStrategyJson 兜底</p>
     * <ul>
     *   <li>若云端配置存在，直接返回，不解析 defaultStrategyJson</li>
     *   <li>若云端配置不存在，再解析 defaultStrategyJson 作为兜底</li>
     * </ul>
     *
     * @param slotId              广告位 ID（8 位数字）
     * @param defaultStrategyJson 默认策略 JSON（兜底，云端无配置时使用）
     * @return AdUnitConfig，若两者均无则返回 null
     */
    public AdUnitConfig getMergedUnitConfig(String slotId, String defaultStrategyJson) {
        // 1. 确保该 slotId 的缓存已加载
        ensureSlotConfigLoaded(slotId);

        // 2. 优先返回云端（本地缓存）配置
        AdUnitConfig cloudUnit = slotConfigMap.get(slotId);
        if (cloudUnit != null) {
            return cloudUnit;
        }

        // 3. 云端无配置，再解析 defaultStrategyJson 兜底
        if (defaultStrategyJson != null && !defaultStrategyJson.isEmpty()) {
            return parseDefaultStrategyJson(slotId, defaultStrategyJson);
        }

        return null;
    }

    /**
     * 确保指定 slotId 的配置已加载（内存 or 本地缓存）。
     * 如果都没有，且配置了云端 URL，则触发异步拉取。
     */
    private void ensureSlotConfigLoaded(String slotId) {
        if (slotConfigMap.containsKey(slotId)) {
            return; // 内存中已有
        }
        // 尝试从本地缓存恢复
        if (appContext != null) {
            AdUnitConfig cached = loadFromCache(appContext, slotId);
            if (cached != null) {
                slotConfigMap.put(slotId, cached);
                FsLogger.i("CloudStrategyManager", "Slot config restored from cache: slotId=" + slotId);
                return;
            }
        }
        // 触发异步拉取（如果有 URL）
        if (configUrl != null && !configUrl.isEmpty() && appContext != null) {
            fetchFromCloud(appContext, slotId, configUrl);
        }
    }

    /**
     * 将默认策略 JSON 解析为 AdUnitConfig（最低优先级）。
     * <p>新版 schema：顶层 {@code strategy} 下放单条 {@link AdUnitConfig}，
     * 其 {@code slotId} 必须与 {@code slotId} 参数一致，否则视为"未找到策略"返回 null。</p>
     */
    private AdUnitConfig parseDefaultStrategyJson(String slotId, String json) {
        try {
            CloudConfig cfg = CloudConfig.fromJson(json);
            AdUnitConfig unit = cfg.getStrategy();
            if (unit == null) {
                FsLogger.w("CloudStrategyManager", "defaultStrategyJson has no 'strategy' field for slotId=" + slotId);
                return null;
            }
            // slotId 校验：业务方传入的 slotId 必须与 JSON 内的 slotId 一致
            if (unit.getSlotId() == null || !unit.getSlotId().equals(slotId)) {
                FsLogger.w("CloudStrategyManager", "defaultStrategyJson slotId mismatch: request=" + slotId
                        + " json=" + unit.getSlotId() + ", skip fallback");
                return null;
            }
            return unit;
        } catch (Exception e) {
            FsLogger.w("CloudStrategyManager", "Failed to parse defaultStrategyJson for slotId=" + slotId, e);
            return null;
        }
    }

    /**
     * 手动触发指定 slotId 的云端配置刷新。
     */
    public void refreshConfig(Context context, String slotId, String configUrl) {
        if (configUrl != null && !configUrl.isEmpty()) {
            fetchFromCloud(context.getApplicationContext(), slotId, configUrl);
        }
    }

    /**
     * 获取指定 slotId 的云端配置版本号。
     */
    public long getCloudVersion(String slotId) {
        CloudConfig cfg = getCachedConfig(slotId);
        return cfg != null ? cfg.getVersion() : 0;
    }

    /**
     * 检查指定 slotId 是否正在拉取云端配置。
     */
    public boolean isFetchInProgress(String slotId) {
        Boolean inProgress = slotFetchInProgress.get(slotId);
        return inProgress != null && inProgress;
    }

    // ═══════════════════════════════════════════════════════════
    // Internal: Cache (按 slotId 隔离)
    // ═══════════════════════════════════════════════════════════

    private AdUnitConfig loadFromCache(Context context, String slotId) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String key = String.format(KEY_CLOUD_CONFIG_FMT, slotId);
            String json = prefs.getString(key, null);
            if (json != null && !json.isEmpty()) {
                CloudConfig cfg = CloudConfig.fromJson(json);
                AdUnitConfig unit = cfg.getStrategy();
                if (unit != null) return unit;
            }
        } catch (Exception e) {
            FsLogger.w("CloudStrategyManager", "Failed to load cached cloud config for slotId=" + slotId, e);
        }
        return null;
    }

    private void saveToCache(Context context, String slotId, String json, long version) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String configKey = String.format(KEY_CLOUD_CONFIG_FMT, slotId);
            String versionKey = String.format(KEY_CLOUD_VERSION_FMT, slotId);
            prefs.edit()
                    .putString(configKey, json)
                    .putLong(versionKey, version)
                    .apply();
            FsLogger.i("CloudStrategyManager", "Cloud config saved to cache: slotId=" + slotId + ", version=" + version);
        } catch (Exception e) {
            FsLogger.w("CloudStrategyManager", "Failed to save cloud config to cache for slotId=" + slotId, e);
        }
    }

    /**
     * 读取指定 slotId 的缓存原始 JSON（用于 fetchFromCloud 内部判断版本号）。
     */
    private CloudConfig getCachedConfig(String slotId) {
        if (appContext == null) return null;
        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String key = String.format(KEY_CLOUD_CONFIG_FMT, slotId);
            String json = prefs.getString(key, null);
            if (json != null && !json.isEmpty()) {
                return CloudConfig.fromJson(json);
            }
        } catch (Exception e) {
            FsLogger.w("CloudStrategyManager", "Failed to read cached cloud config for slotId=" + slotId, e);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    // Internal: Network Fetch (按 slotId 拉取)
    // ═══════════════════════════════════════════════════════════

    private void fetchFromCloud(Context context, String slotId, String configUrl) {
        Boolean inProgress = slotFetchInProgress.get(slotId);
        if (inProgress != null && inProgress) {
            FsLogger.d("CloudStrategyManager", "Cloud config fetch already in progress for slotId=" + slotId + ", skip");
            return;
        }
        slotFetchInProgress.put(slotId, true);
        FsLogger.d("CloudStrategyManager", "Fetching cloud config for slotId=" + slotId + " from: " + configUrl);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(buildFetchUrl(configUrl, slotId));
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoInput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String response = readStream(conn.getInputStream());
                    FsLogger.d("CloudStrategyManager", "Cloud config response for slotId=" + slotId + ": "
                            + response.substring(0, Math.min(response.length(), 200)) + "...");

                    CloudConfig newConfig = CloudConfig.fromJson(response);
                    AdUnitConfig newUnit = newConfig.getStrategy();

                    // slotId 校验：云端返回的配置 slotId 必须与请求一致
                    if (newUnit == null || newUnit.getSlotId() == null
                            || !newUnit.getSlotId().equals(slotId)) {
                        FsLogger.w("CloudStrategyManager", "Cloud config slotId mismatch: request=" + slotId
                                + " response=" + (newUnit != null ? newUnit.getSlotId() : "null")
                                + ", discard");
                        return;
                    }

                    // 版本号比较：只有新版本才更新
                    long currentVersion = getCloudVersion(slotId);
                    if (newConfig.getVersion() > currentVersion) {
                        slotConfigMap.put(slotId, newUnit);
                        saveToCache(context, slotId, response, newConfig.getVersion());
                        FsLogger.i("CloudStrategyManager", "Cloud config updated: slotId=" + slotId
                                + ", version=" + newConfig.getVersion());
                    } else {
                        FsLogger.d("CloudStrategyManager", "Cloud config version " + newConfig.getVersion()
                                + " <= current " + currentVersion + " for slotId=" + slotId + ", skip update");
                    }
                } else {
                    FsLogger.w("CloudStrategyManager", "Cloud config fetch failed for slotId=" + slotId + ", HTTP " + responseCode);
                }
            } catch (Exception e) {
                FsLogger.w("CloudStrategyManager", "Cloud config fetch error for slotId=" + slotId, e);
            } finally {
                if (conn != null) conn.disconnect();
                slotFetchInProgress.put(slotId, false);
            }
        });
    }

    /**
     * 构建带 slotId 参数的拉取 URL。
     */
    private String buildFetchUrl(String baseUrl, String slotId) {
        if (baseUrl.contains("?")) {
            return baseUrl + "&slotId=" + slotId;
        } else {
            return baseUrl + "?slotId=" + slotId;
        }
    }

    private String readStream(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
