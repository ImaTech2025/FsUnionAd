package com.ima.fs.unionad;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.ima.union.core.config.CloudConfig;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdSdkType;
import com.ima.union.core.model.AdSourceConfig;
import com.ima.union.core.model.AdUnitConfig;
import com.ima.union.core.model.StrategyType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

    /**
     * Demo 广告格式页的抽象基类。
     *
     * <p>统一处理：
     * <ul>
     *   <li>广告源展示（ChipGroup，只读，从JSON策略读取）</li>
     *   <li>策略单选（RadioGroup）</li>
     *   <li>日志面板</li>
     * </ul>
     *
     * <p>子类只需实现 {@link #getAdFormat()}、{@link #getSlotId(String)}、{@link #getAppId(String)}
     * 以及各自的广告加载/展示逻辑。</p>
     */
    public abstract class BaseAdActivity extends Activity {

    private static final String TAG = "BaseAdActivity";

    protected final Handler mHandler = new Handler(Looper.getMainLooper());

    // ── Views ──
    private ChipGroup mSourceChipGroup;
    private android.widget.RadioGroup mStrategyGroup;
    private TextView mInfoText;
    private NestedScrollView mLogsScroll;
    private TextView mLogsText;

    // ── State ──
    protected String mDefaultStrategyJson;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        // Toolbar back
        View toolbar = findViewById(com.ima.fs.unionad.R.id.toolbar);
        if (toolbar != null) toolbar.setOnClickListener(v -> finish());

        findViews();

        // 先读取 assets 默认策略 JSON，setupSourceSelector 需要解析其中已配置的广告源
        String assetName = getDefaultStrategyAssetName();
        mDefaultStrategyJson = readAssetsJson(assetName);
        if (mDefaultStrategyJson != null) {
            LogProxy.i(getActivityTag(), "已从 assets 加载策略配置: " + assetName);
        }

        setupSourceSelector();
        setupStrategySelector();
        setupLogControls();

        updateInfoDisplay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }

    // ═══════════════════════════════════════════════════════════
    // Abstract / Override hooks
    // ═══════════════════════════════════════════════════════════

    @LayoutRes
    protected abstract int getLayoutResId();

    protected abstract String getActivityTag();

    protected abstract AdFormat getAdFormat();

    protected abstract String getSlotId(String sourceKey);

    protected abstract String getAppId(String sourceKey);

    /**
     * 返回当前广告格式对应的聚合 SDK slotId（8 位数字）。
     */
    protected abstract String getSlotIdKey();

    /**
     * 返回当前广告格式对应的默认策略 JSON assets 文件名。
     * 子类覆盖以读取各类型独立的策略配置。
     */
    protected abstract String getDefaultStrategyAssetName();

    // ═══════════════════════════════════════════════════════════
    // View Finding
    // ═══════════════════════════════════════════════════════════

    private void findViews() {
        mSourceChipGroup = findViewById(com.ima.fs.unionad.R.id.source_chip_group);
        mStrategyGroup = findViewById(com.ima.fs.unionad.R.id.strategy_radio_group);
        mInfoText = findViewById(com.ima.fs.unionad.R.id.info_text);
        mLogsScroll = findViewById(com.ima.fs.unionad.R.id.logs_scroll);
        mLogsText = findViewById(com.ima.fs.unionad.R.id.logs_text);
    }

    // ═══════════════════════════════════════════════════════════
    // Source Display (ChipGroup, read-only from JSON config)
    // ═══════════════════════════════════════════════════════════

    private void setupSourceSelector() {
        if (mSourceChipGroup == null) return;

        mSourceChipGroup.removeAllViews();
        mSourceChipGroup.setSelectionRequired(false);

        // 从 JSON 策略中解析已配置的广告源并展示为只读 Chip
        List<AdSourceConfig> configuredSources = parseConfiguredSources();
        for (AdSourceConfig src : configuredSources) {
            Chip chip = new Chip(this);
            chip.setText(sourceLabelFromSdkType(src.getSdkType()));
            chip.setCheckable(false);    // 只读，不可交互
            chip.setEnabled(false);      // 禁用点击
            chip.setEnsureMinTouchTargetSize(false);
            mSourceChipGroup.addView(chip);
        }

        if (configuredSources.isEmpty()) {
            Chip emptyChip = new Chip(this);
            emptyChip.setText("未配置广告源");
            emptyChip.setCheckable(false);
            emptyChip.setEnabled(false);
            mSourceChipGroup.addView(emptyChip);
        }
    }

    /**
     * 从 assets JSON 策略中解析已配置的广告源列表。
     * <p>返回所有阶段（按 priority 升序）的全部 source 扁平化列表，
     * 避免多阶段配置下只展示第一阶段 sources。</p>
     */
    private List<AdSourceConfig> parseConfiguredSources() {
        if (mDefaultStrategyJson == null || mDefaultStrategyJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            CloudConfig cloudConfig = CloudConfig.fromJson(mDefaultStrategyJson);
            String slotId = getSlotIdKey();
            AdUnitConfig cloudUnit = cloudConfig.getAdUnitConfig(slotId);
            if (cloudUnit == null) {
                return Collections.emptyList();
            }
            // priority chain 模式下, 扁平化所有阶段的 sources 一起展示
            return new ArrayList<>(cloudUnit.getAllSourcesFlat());
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse configured sources from JSON", e);
            return Collections.emptyList();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Strategy Display (read-only from JSON config)
    // ═══════════════════════════════════════════════════════════

    private void setupStrategySelector() {
        if (mStrategyGroup == null) return;
        // 根据 JSON 配置设置默认选中状态：
        //   strategies.size() >= 2 → 选中 hybrid 单选（priority chain）
        //   strategies.size() == 1, type=BIDDING   → 选中 bidding 单选
        //   strategies.size() == 1, type=WATERFALL → 选中 waterfall 单选
        int checkId;
        if (isHybridFromJson()) {
            checkId = com.ima.fs.unionad.R.id.radio_strategy_hybrid;
        } else {
            StrategyType jsonType = getJsonStrategyType();
            switch (jsonType) {
                case BIDDING: checkId = com.ima.fs.unionad.R.id.radio_strategy_bidding; break;
                default:      checkId = com.ima.fs.unionad.R.id.radio_strategy_waterfall; break;
            }
        }
        mStrategyGroup.check(checkId);
        // 禁用交互：策略由 JSON 配置决定
        for (int i = 0; i < mStrategyGroup.getChildCount(); i++) {
            mStrategyGroup.getChildAt(i).setEnabled(false);
        }
    }

    /**
     * 从当前 JSON 策略配置中解析策略类型。
     */
    protected StrategyType getJsonStrategyType() {
        if (mDefaultStrategyJson == null || mDefaultStrategyJson.isEmpty()) {
            return StrategyType.WATERFALL;
        }
        try {
            CloudConfig cloudConfig = CloudConfig.fromJson(mDefaultStrategyJson);
            AdUnitConfig cloudUnit = cloudConfig.getAdUnitConfig(getSlotIdKey());
            if (cloudUnit != null && cloudUnit.getStrategyType() != null) {
                return cloudUnit.getStrategyType();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse strategyType from JSON", e);
        }
        return StrategyType.WATERFALL;
    }

    /**
     * 判断当前 JSON 策略是否为 priority chain（strategies.size() >= 2）。
     */
    protected boolean isHybridFromJson() {
        if (mDefaultStrategyJson == null || mDefaultStrategyJson.isEmpty()) {
            return false;
        }
        try {
            CloudConfig cloudConfig = CloudConfig.fromJson(mDefaultStrategyJson);
            AdUnitConfig cloudUnit = cloudConfig.getAdUnitConfig(getSlotIdKey());
            return cloudUnit != null && cloudUnit.isHybrid();
        } catch (Exception e) {
            return false;
        }
    }


    // ═══════════════════════════════════════════════════════════
    // Info Display
    // ═══════════════════════════════════════════════════════════

    protected void updateInfoDisplay() {
        if (mInfoText == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Strategy: ").append(strategyLabel()).append(" (JSON配置)").append("\n");

        List<AdSourceConfig> sources = parseConfiguredSources();
        sb.append("Sources (").append(sources.size()).append("):\n");
        for (AdSourceConfig src : sources) {
            sb.append("  • ").append(sourceLabelFromSdkType(src.getSdkType()))
              .append(" | AppID: ").append(src.getAppId())
              .append(" | SlotID: ").append(src.getAdUnitId()).append("\n");
        }
        mInfoText.setText(sb.toString().trim());
    }

    // ═══════════════════════════════════════════════════════════
    // Logs
    // ═══════════════════════════════════════════════════════════

    private void setupLogControls() {
        View btnClear = findViewById(com.ima.fs.unionad.R.id.btn_clear_logs);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                LogProxy.clear(getActivityTag());
                if (mLogsText != null) mLogsText.setText("日志已清空");
                LogProxy.i(getActivityTag(), "--- 日志已清空 ---");
            });
        }
    }

    protected void refreshLogs() {
        if (mLogsText == null || mLogsScroll == null) return;
        List<String> logs = LogProxy.getLogs(getActivityTag());
        if (logs.isEmpty()) {
            mLogsText.setText("等待操作...");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = logs.size() - 1; i >= 0; i--) {
            sb.append(logs.get(i));
            if (i > 0) sb.append("\n");
        }
        String text = sb.toString();
        SpannableString ss = new SpannableString(text);
        int errColor = getColor(com.ima.fs.unionad.R.color.log_error);
        int idx = 0;
        for (String line : text.split("\n")) {
            if (line.contains("失败") || line.contains("[E]") || line.contains("Error")) {
                int end = idx + line.length();
                if (end <= text.length())
                    ss.setSpan(new ForegroundColorSpan(errColor), idx, end, 0);
            }
            idx += line.length() + 1;
        }
        mLogsText.setText(ss);
        mLogsScroll.post(() -> mLogsScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * 将 AdSdkType 映射为展示标签。
     */
    protected String sourceLabelFromSdkType(AdSdkType type) {
        if (type == null) return "未知";
        if (type == AdSdkType.PANGLE)  return "穿山甲 Pangle";
        if (type == AdSdkType.GDT)     return "优量汇 GDT";
        if (type == AdSdkType.BAIDU)   return "百青藤 Baidu";
        if (type == AdSdkType.FISSION) return "飞梭 Fission";
        return type.getSdkName();  // 自定义平台类型使用其 sdkName
    }

    protected String strategyLabel() {
        // priority chain 模式展示为 "Priority Chain (N 阶段)"
        if (isHybridFromJson()) {
            try {
                CloudConfig cloudConfig = CloudConfig.fromJson(mDefaultStrategyJson);
                AdUnitConfig cloudUnit = cloudConfig.getAdUnitConfig(getSlotIdKey());
                int n = cloudUnit != null ? cloudUnit.getStrategies().size() : 0;
                return "Priority Chain (共 " + n + " 阶段)";
            } catch (Exception e) {
                return "Priority Chain";
            }
        }
        switch (getJsonStrategyType()) {
            case BIDDING: return "Bidding 实时竞价";
            default:      return "Waterfall 瀑布流";
        }
    }

    /**
     * 从 assets 目录读取 JSON 文件内容。
     */
    protected String readAssetsJson(String fileName) {
        try (InputStream is = getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            Log.w(TAG, "Failed to read assets file: " + fileName, e);
            return null;
        }
    }
}
