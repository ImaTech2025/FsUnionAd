package com.ima.union.core.strategy;

import android.content.Context;

import com.ima.union.core.model.AdRequestParams;
import com.ima.union.core.model.AdUnitConfig;
import com.ima.union.core.model.StrategyType;

public interface AdStrategy {
    StrategyType getStrategyType();
    void execute(Context context, AdRequestParams params, AdUnitConfig unitConfig, AdStrategyCallback callback);
    void cancel();
}
