package com.ima.union.core.strategy;

import com.ima.union.core.model.UnionAdResponse;

public interface AdStrategyCallback {
    void onAdLoaded(UnionAdResponse response);
    void onNoFill(String reason);
}
