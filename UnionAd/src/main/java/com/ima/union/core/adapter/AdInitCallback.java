package com.ima.union.core.adapter;

public interface AdInitCallback {
    void onInitSuccess();
    void onInitFailure(int errorCode, String errorMsg);
}
