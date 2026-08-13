package com.ima.union.core.model;

import com.ima.union.core.model.UnionAdResponse;

/**
 * 广告请求结果包装。
 *
 * <p>内部使用：策略引擎在适配器 {@code request()} 调用过程中会持有一个 {@code AdLoadResult}
 * 引用，请求成功时通过 {@link Success#setResponse(UnionAdResponse)} 写入，
 * 失败时通过 {@link Failure} 写入。读取端通过类型判断提取响应或错误信息。</p>
 */
public class AdLoadResult {

    public static class Success extends AdLoadResult {
        private UnionAdResponse response;
        public Success() { }
        public Success(UnionAdResponse response) { this.response = response; }
        public void setResponse(UnionAdResponse response) { this.response = response; }
        public UnionAdResponse getResponse() { return response; }
    }

    public static class Failure extends AdLoadResult {
        private final String sdkName;
        private final int errorCode;
        private final String errorMsg;
        public Failure(String sdkName, int errorCode, String errorMsg) {
            this.sdkName = sdkName;
            this.errorCode = errorCode;
            this.errorMsg = errorMsg;
        }
        public String getSdkName() { return sdkName; }
        public int getErrorCode() { return errorCode; }
        public String getErrorMsg() { return errorMsg; }
    }
}
