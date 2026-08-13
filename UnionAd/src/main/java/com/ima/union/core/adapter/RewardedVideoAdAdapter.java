package com.ima.union.core.adapter;

import android.content.Context;
import com.ima.union.core.model.UnionAdResponse;

public interface RewardedVideoAdAdapter extends AdAdapter {
    void showRewardedVideo(Context context, UnionAdResponse response, RewardedVideoAdListener listener);
}
