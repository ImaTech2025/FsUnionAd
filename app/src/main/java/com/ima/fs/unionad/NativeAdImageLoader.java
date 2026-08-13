package com.ima.fs.unionad;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轻量级图片加载器，用于自渲染广告素材的异步加载。
 *
 * <p>使用 {@link LruCache} 做内存缓存 + {@link ExecutorService} 做线程池，
 * 通过 URL tag 防止 ImageView 复用时的图片错位。</p>
 */
public class NativeAdImageLoader {

    private static final int CACHE_BYTES = 4 * 1024 * 1024; // 4MB
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private static volatile NativeAdImageLoader sInstance;

    private final LruCache<String, Bitmap> mCache;
    private final ExecutorService mExecutor;
    private final Handler mMainHandler;

    private NativeAdImageLoader() {
        mCache = new LruCache<String, Bitmap>(CACHE_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        mExecutor = Executors.newFixedThreadPool(3);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public static NativeAdImageLoader getInstance() {
        if (sInstance == null) {
            synchronized (NativeAdImageLoader.class) {
                if (sInstance == null) {
                    sInstance = new NativeAdImageLoader();
                }
            }
        }
        return sInstance;
    }

    /**
     * 异步加载图片到 ImageView。
     *
     * @param url    图片 URL
     * @param target 目标 ImageView
     */
    public void loadImage(String url, ImageView target) {
        if (url == null || url.isEmpty() || target == null) {
            return;
        }

        // 用 URL 作为 tag 防止复用错位
        target.setTag(url);

        // 先查缓存
        Bitmap cached = mCache.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        mExecutor.execute(() -> {
            HttpURLConnection conn = null;
            InputStream is = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoInput(true);
                conn.connect();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    is = conn.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        mCache.put(url, bitmap);
                        final String tag = (String) target.getTag();
                        if (url.equals(tag)) {
                            mMainHandler.post(() -> {
                                if (url.equals(target.getTag())) {
                                    target.setImageBitmap(bitmap);
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                // 静默失败，placeholder 已在背景中设置
            } finally {
                if (is != null) {
                    try { is.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public void clearCache() {
        mCache.evictAll();
    }
}
