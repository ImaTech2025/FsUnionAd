package com.ima.fs.unionad;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.ima.union.core.adapter.AdAdapter;
import com.ima.union.core.adapter.AdAdapterRegistry;
import com.ima.union.core.model.AdSdkType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FsUnionSDK Demo 主入口 — 两列平台集成状态 + 跳转各格式独立 Demo 页。
 *
 * <p>已注册适配器即为已集成，展示每个平台下适配器数量、版本、Bidding 等信息。</p>
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Main";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private GridLayout mPlatformCardsContainer;
    private NestedScrollView mLogsScroll;
    private TextView mLogsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout),
                (v, insets) -> {
                    v.setPadding(0, insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()).top, 0, 0);
                    return insets;
                });

        findViews();
        setupNavigationButtons();
        setupLogControls();

        refreshAllPlatformCards();

        LogProxy.i(TAG, "MainActivity onCreate — Demo hub ready");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMainHandler.removeCallbacksAndMessages(null);
    }

    private void findViews() {
        mPlatformCardsContainer = findViewById(R.id.platform_cards_container);
        mLogsScroll = findViewById(R.id.logs_scroll);
        mLogsText = findViewById(R.id.logs_text);
    }

    // ═══════════════════════════════════════════════════════════
    // Dynamic Platform Cards (Two-column GridLayout)
    // ═══════════════════════════════════════════════════════════

    private void refreshAllPlatformCards() {
        if (mPlatformCardsContainer == null) return;
        mPlatformCardsContainer.removeAllViews();

        AdAdapterRegistry registry = AdAdapterRegistry.getInstance();
        List<AdAdapter> allAdapters = registry.getAllAdapters();

        // 按 SDK 类型分组，统计每个平台下适配器数量
        Map<AdSdkType, List<AdAdapter>> platformAdapters = new LinkedHashMap<>();
        for (AdAdapter adapter : allAdapters) {
            AdSdkType type = adapter.getSdkType();
            if (!platformAdapters.containsKey(type)) {
                platformAdapters.put(type, new ArrayList<>());
            }
            platformAdapters.get(type).add(adapter);
        }

        int platformCount = platformAdapters.size();
        int biddingCount = 0;
        for (List<AdAdapter> adapters : platformAdapters.values()) {
            for (AdAdapter a : adapters) {
                if (a.supportBidding()) {
                    biddingCount++;
                    break;  // 一个平台只要有一个支持 Bidding 就算
                }
            }
        }

        float density = getResources().getDisplayMetrics().density;

        // 摘要行（跨两列）
        if (platformCount > 0) {
            TextView summary = new TextView(this);
            StringBuilder sb = new StringBuilder();
            sb.append("已集成 ").append(platformCount).append(" 个平台");
            if (biddingCount > 0) {
                sb.append("，").append(biddingCount).append(" 个支持 Bidding");
            }
            summary.setText(sb.toString());
            summary.setTextSize(13);
            summary.setTextColor(getColor(R.color.on_surface_variant));

            GridLayout.LayoutParams sp = new GridLayout.LayoutParams();
            sp.width = 0;
            sp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            sp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 2, 1f);
            sp.setMargins((int) (20 * density), (int) (12 * density), (int) (20 * density), (int) (8 * density));
            summary.setLayoutParams(sp);
            mPlatformCardsContainer.addView(summary);
        }

        int cardIndex = 0;
        for (Map.Entry<AdSdkType, List<AdAdapter>> entry : platformAdapters.entrySet()) {
            View card = createPlatformCard(entry.getValue(), cardIndex);
            mPlatformCardsContainer.addView(card);
            cardIndex++;
        }

        if (platformCount == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂无已注册适配器");
            empty.setTextSize(14);
            empty.setTextColor(getColor(R.color.on_surface_variant));

            GridLayout.LayoutParams ep = new GridLayout.LayoutParams();
            ep.width = 0;
            ep.height = GridLayout.LayoutParams.WRAP_CONTENT;
            ep.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 2, 1f);
            ep.setMargins((int) (20 * density), (int) (12 * density), 0, 0);
            empty.setLayoutParams(ep);
            mPlatformCardsContainer.addView(empty);
        }
    }

    private View createPlatformCard(List<AdAdapter> adapters, int index) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_platform_card, mPlatformCardsContainer, false);

        float density = getResources().getDisplayMetrics().density;
        int gutter = (int) (6 * density);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        // 左列：右间距；右列：左间距，保持两列均匀有间距
        params.setMargins(
                (index % 2 == 0) ? 0 : gutter,  // left
                0,                                // top
                (index % 2 == 0) ? gutter : 0,   // right
                gutter                                 // bottom
        );
        card.setLayoutParams(params);

        ImageView icon = card.findViewById(R.id.card_status_icon);
        TextView title = card.findViewById(R.id.card_title);
        TextView info = card.findViewById(R.id.card_info);
        TextView badge = card.findViewById(R.id.card_badge);

        int readyColor = getColor(R.color.status_ready);

        // 取第一个适配器获取元信息
        AdAdapter first = adapters.get(0);
        title.setText(first.getSdkType().getSdkName());

        // 判断该平台是否有 Bidding 能力
        boolean hasBidding = false;
        for (AdAdapter a : adapters) {
            if (a.supportBidding()) {
                hasBidding = true;
                break;
            }
        }

        // Info line: 适配器数量 + 版本 + Bidding
        String infoText = adapters.size() + " adapter"
                + (adapters.size() > 1 ? "s" : "")
                + " | v" + first.getAdapterVersion()
                + " | Bidding " + (hasBidding ? "✓" : "✗");
        info.setText(infoText);

        // Icon — 统一绿色勾选（已注册即已集成）
        icon.setImageResource(android.R.drawable.checkbox_on_background);
        icon.setColorFilter(readyColor);

        // Badge — 统一「已集成」
        badge.setText("已集成");
        badge.setTextColor(readyColor);
        badge.setBackgroundResource(R.drawable.badge_ready);

        return card;
    }

    // ═══════════════════════════════════════════════════════════
    // Navigation
    // ═══════════════════════════════════════════════════════════

    private void setupNavigationButtons() {
        findViewById(R.id.btn_nav_splash).setOnClickListener(v ->
                startActivity(new Intent(this, SplashAdActivity.class)));
        findViewById(R.id.btn_nav_inter).setOnClickListener(v ->
                startActivity(new Intent(this, InterstitialAdActivity.class)));
        findViewById(R.id.btn_nav_reward).setOnClickListener(v ->
                startActivity(new Intent(this, RewardedVideoAdActivity.class)));
        findViewById(R.id.btn_nav_feed_tp).setOnClickListener(v ->
                startActivity(new Intent(this, FeedTemplateAdActivity.class)));
        findViewById(R.id.btn_nav_feed_rd).setOnClickListener(v ->
                startActivity(new Intent(this, FeedRenderAdActivity.class)));
    }

    // ═══════════════════════════════════════════════════════════
    // Logs
    // ═══════════════════════════════════════════════════════════

    private void setupLogControls() {
        findViewById(R.id.btn_clear_logs).setOnClickListener(v -> {
            LogProxy.clear(TAG);
            if (mLogsText != null) mLogsText.setText("日志已清空");
        });
    }

    private void refreshLogs() {
        if (mLogsText == null || mLogsScroll == null) return;
        List<String> logs = LogProxy.getLogs(TAG);
        if (logs.isEmpty()) {
            mLogsText.setText("等待初始化...");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = logs.size() - 1; i >= 0; i--) {
            sb.append(logs.get(i));
            if (i > 0) sb.append("\n");
        }
        String text = sb.toString();
        SpannableString ss = new SpannableString(text);
        int errorColor = getColor(R.color.log_error);
        int idx = 0;
        for (String line : text.split("\n")) {
            if (line.contains("失败") || line.contains("[E]") || line.contains("Error")) {
                int end = idx + line.length();
                if (end <= text.length())
                    ss.setSpan(new ForegroundColorSpan(errorColor), idx, end, 0);
            }
            idx += line.length() + 1;
        }
        mLogsText.setText(ss);
        mLogsScroll.post(() -> mLogsScroll.fullScroll(View.FOCUS_DOWN));
    }
}
