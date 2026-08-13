package com.ima.fs.unionad;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.ima.union.core.model.entry.IFsUnionNativeAd;
import com.ima.union.core.model.listener.FsUnionNativeAdListener;
import com.ima.union.core.model.AdFormat;
import com.ima.union.core.model.AdRequestParams;
import com.ima.union.manager.FsFeedRenderAdManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 信息流自渲染广告独立展示页 — Load Feed Render 完整流程演示。
 *
 * <p>继承 {@link BaseAdActivity}，广告源多选、策略切换、日志面板全部复用基类。</p>
 *
 * <p><b>尺寸配置</b>：UI 提供两组输入框：
 * <ul>
 *   <li>{@code setImageAcceptedSize(px)} — Pangle &lt; 75xx 必传；75xx+ 可省略
 *       （Pangle 适配器在外部未传时回退到默认 640x320 px）</li>
 *   <li>{@code setExpressViewAcceptedSize(dp)} — 自渲染时与 imageSize 配套；
 *       Pangle &lt; 75xx 兼容性字段；75xx+ 可省略</li>
 * </ul>
 * 留空(0) 时不传递给三方 SDK。</p>
 */
public class FeedRenderAdActivity extends BaseAdActivity {

    private static final String TAG = "FeedRD";

    private IFsUnionNativeAd mNativeAd;
    private FrameLayout mFeedContainer;
    private View mBtnLoad;
    private TextInputEditText mEtImageW;
    private TextInputEditText mEtImageH;
    private TextInputEditText mEtExpressW;
    private TextInputEditText mEtExpressH;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_feed_render;
    }

    @Override
    protected String getActivityTag() {
        return TAG;
    }

    @Override
    protected AdFormat getAdFormat() {
        return AdFormat.FEED_RENDER;
    }

    @Override
    protected String getSlotId(String sourceKey) {
        switch (sourceKey) {
            case "pangle":  return FsUnionAdApp.PANGLE_FEED_RD_ID;
            case "baidu":   return FsUnionAdApp.BAIDU_FEED_RD_ID;
            case "custom":  return "custom_feed_rd_slot";
            default:        return FsUnionAdApp.FISSION_FEED_RD_ID;
        }
    }

    @Override
    protected String getAppId(String sourceKey) {
        switch (sourceKey) {
            case "pangle":  return FsUnionAdApp.PANGLE_APP_ID;
            case "baidu":   return FsUnionAdApp.BAIDU_APP_ID;
            case "custom":  return "demo_custom_app";
            default:        return FsUnionAdApp.FISSION_APP_ID;
        }
    }

    @Override
    protected String getSlotIdKey() {
        return "10000005";
    }

    @Override
    protected String getDefaultStrategyAssetName() {
        return "ad_strategy_feed_render.json";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFeedContainer = findViewById(R.id.feed_container);
        mBtnLoad = findViewById(R.id.btn_load);
        mEtImageW = findViewById(R.id.et_image_w);
        mEtImageH = findViewById(R.id.et_image_h);
        mEtExpressW = findViewById(R.id.et_express_w);
        mEtExpressH = findViewById(R.id.et_express_h);
        setupActions();

        LogProxy.d(TAG, "FeedRenderAdActivity onCreate");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mNativeAd != null) {
            mNativeAd.destroy();
            mNativeAd = null;
        }
        LogProxy.d(TAG, "FeedRenderAdActivity onDestroy");
    }

    private void setupActions() {
        mBtnLoad.setOnClickListener(v -> loadFeedRender());
    }

    private void loadFeedRender() {
        LogProxy.i(TAG, "========== 开始加载信息流自渲染 ==========");
        LogProxy.i(TAG, "SlotId: " + getSlotIdKey() + " | Strategy: " + strategyLabel());

        if (mFeedContainer != null) {
            mFeedContainer.removeAllViews();
        }
        mNativeAd = null;

        // ── 读取尺寸输入框 ──
        int imgW = parseEditInt(mEtImageW, 0);
        int imgH = parseEditInt(mEtImageH, 0);
        int expressW = parseEditInt(mEtExpressW, 0);
        int expressH = parseEditInt(mEtExpressH, 0);

        AdRequestParams.Builder paramsBuilder = new AdRequestParams.Builder()
                .slotId(getSlotIdKey())
                .defaultStrategyJson(mDefaultStrategyJson);
        if (imgW > 0 && imgH > 0) {
            paramsBuilder.setImageAcceptedSize(imgW, imgH);
            LogProxy.i(TAG, "setImageAcceptedSize: " + imgW + "x" + imgH + " px");
        } else {
            LogProxy.w(TAG, "setImageAcceptedSize: 留空(0)，Pangle 适配器走默认 640x320 px");
        }
        if (expressW > 0 && expressH > 0) {
            paramsBuilder.setExpressViewAcceptedSize(expressW, expressH);
            LogProxy.i(TAG, "setExpressViewAcceptedSize: " + expressW + "x" + expressH + " dp");
        } else {
            LogProxy.w(TAG, "setExpressViewAcceptedSize: 留空(0)，不传递给三方 SDK");
        }

        FsFeedRenderAdManager.loadAd(this, paramsBuilder.build(), new FsFeedRenderAdManager.OnFsNativeAdLoadListener() {
            @Override
            public void onAdLoaded(IFsUnionNativeAd ad) {
                mNativeAd = ad;
                LogProxy.i(TAG, "信息流自渲染加载成功: " + ad.getSdkName()
                        + " ecpm=" + ad.getEcpm());
                renderNativeAd(ad);
                refreshLogs();
            }

            @Override
            public void onAdLoadError(int errorCode, String errorMsg) {
                LogProxy.e(TAG, "信息流自渲染加载失败 [" + errorCode + "]: " + errorMsg);
                refreshLogs();
            }
        });

        refreshLogs();
    }

    private void renderNativeAd(IFsUnionNativeAd ad) {
        LogProxy.i(TAG, "自渲染数据: title=" + ad.getTitle()
                + ", desc=" + safeStr(ad.getDescription())
                + ", cta=" + safeStr(ad.getCallToAction())
                + ", iconUrl=" + safeStr(ad.getIconUrl())
                + ", imageUrl=" + safeStr(ad.getImageUrl())
                + ", rating=" + ad.getRating()
                + ", isDownloadAd=" + ad.isDownloadAd()
                + ", appSize=" + ad.getAppSize()
                + ", source=" + ad.getSdkName());

        // ── Inflate 卡片布局 ──
        View adView = getLayoutInflater().inflate(R.layout.item_native_ad, mFeedContainer, false);

        // ── 标题 ──
        TextView titleTv = adView.findViewById(R.id.ad_title);
        titleTv.setText(ad.getTitle());

        // ── 描述 ──
        TextView descTv = adView.findViewById(R.id.ad_desc);
        String desc = ad.getDescription();
        if (!TextUtils.isEmpty(desc)) {
            descTv.setText(desc);
            descTv.setVisibility(View.VISIBLE);
        }

        // ── 图标（App Logo）──
        ShapeableImageView iconIv = adView.findViewById(R.id.ad_icon);
        String iconUrl = ad.getIconUrl();
        if (!TextUtils.isEmpty(iconUrl)) {
            NativeAdImageLoader.getInstance().loadImage(iconUrl, iconIv);
        }

        // ── 主图 ──
        ShapeableImageView mainImageIv = adView.findViewById(R.id.ad_main_image);
        String imageUrl = ad.getImageUrl();
        if (TextUtils.isEmpty(imageUrl)) {
            List<String> imageList = ad.getImageList();
            if (imageList != null && !imageList.isEmpty()) {
                imageUrl = imageList.get(0);
            }
        }
        if (!TextUtils.isEmpty(imageUrl)) {
            mainImageIv.setVisibility(View.VISIBLE);
            NativeAdImageLoader.getInstance().loadImage(imageUrl, mainImageIv);
        }

        // ── Meta 行: 评分 / 应用大小 / 广告类型 ──
        TextView metaTv = adView.findViewById(R.id.ad_meta);
        StringBuilder meta = new StringBuilder();
        if (ad.getRating() > 0) {
            meta.append(String.format(Locale.getDefault(), "%.1f分", ad.getRating()));
        }
        if (ad.getAppSize() > 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(formatAppSize(ad.getAppSize()));
        }
        if (meta.length() > 0) meta.append(" · ");
        meta.append(ad.isDownloadAd() ? "应用下载" : "落地页");
        metaTv.setText(meta.toString());
        metaTv.setVisibility(View.VISIBLE);

        // ── 底部栏: 广告标识 + 来源 + CTA ──
        TextView ctaTv = adView.findViewById(R.id.ad_cta);
        String cta = ad.getCallToAction();
        ctaTv.setText(!TextUtils.isEmpty(cta) ? cta : "查看详情");

        TextView sourceTv = adView.findViewById(R.id.ad_source);
        sourceTv.setText("via " + ad.getSdkName() + " | ecpm=" + ad.getEcpm());

        // ── 卡片根布局（点击区域之一）──
        MaterialCardView cardRoot = adView.findViewById(R.id.ad_card_root);

        mHandler.post(() -> {
            if (mFeedContainer != null) {
                mFeedContainer.addView(adView);

                // ── 注册点击交互区域（必须在 View 添加到 Window 之后调用）──
                // 将整个卡片和 CTA 按钮注册为可点击 View，SDK 接管点击事件
                // 处理落地页跳转和曝光/点击计费。
                List<View> clickViews = new ArrayList<>();
                clickViews.add(cardRoot);
                clickViews.add(ctaTv);
                ad.registerViewForInteraction((ViewGroup) adView, clickViews, new FsUnionNativeAdListener() {
                    @Override public void onAdShow(IFsUnionNativeAd ad) {
                        LogProxy.i(TAG, "自渲染广告曝光: " + ad.getSdkName());
                        refreshLogs();
                    }
                    @Override public void onAdClick(IFsUnionNativeAd ad) {
                        LogProxy.i(TAG, "自渲染广告点击: " + ad.getSdkName());
                        refreshLogs();
                    }
                    @Override public void onAdError(IFsUnionNativeAd ad, int code, String msg) {
                        LogProxy.e(TAG, "自渲染广告错误 [" + code + "]: " + msg);
                        refreshLogs();
                    }
                });

                LogProxy.i(TAG, "自渲染视图已添加，交互注册完成 (title=" + ad.getTitle()
                        + ", clickViews=" + clickViews.size() + ")");
                refreshLogs();
            }
        });
    }

    /** 格式化应用大小（字节 → KB/MB） */
    private String formatAppSize(long bytes) {
        if (bytes <= 0) return "";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1) {
            return String.format(Locale.getDefault(), "%.1fMB", mb);
        }
        double kb = bytes / 1024.0;
        return String.format(Locale.getDefault(), "%.0fKB", kb);
    }

    /** null 安全的字符串打印 */
    private String safeStr(String s) {
        return s != null ? s : "n/a";
    }

    /**
     * 解析 TextInputEditText 的整数内容。空 / 非数字返回 0（让 Pangle 适配器走默认）。
     */
    private int parseEditInt(TextInputEditText et, int fallback) {
        if (et == null) return fallback;
        String s = et.getText() != null ? et.getText().toString() : "";
        if (TextUtils.isEmpty(s)) return fallback;
        try {
            int v = Integer.parseInt(s.trim());
            return v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
