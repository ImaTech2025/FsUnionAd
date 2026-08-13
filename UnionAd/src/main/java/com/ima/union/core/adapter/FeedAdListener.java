package com.ima.union.core.adapter;

public interface FeedAdListener extends AdEventListener {
    void onFeedAdRendered();
    default void onFeedAdDislike() {}
}
