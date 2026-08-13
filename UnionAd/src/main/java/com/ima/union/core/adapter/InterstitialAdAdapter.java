package com.ima.union.core.adapter;

import android.content.Context;
import com.ima.union.core.model.UnionAdResponse;

public interface InterstitialAdAdapter extends AdAdapter {
    void showInterstitial(Context context, UnionAdResponse response, InterstitialAdListener listener);
}
