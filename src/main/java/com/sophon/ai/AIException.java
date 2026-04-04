package com.sophon.ai;

/** AI 供应商调用失败（网络、HTTP、业务错误等）。 */
public class AIException extends Exception {

    public AIException(String message) {
        super(message);
    }

    public AIException(String message, Throwable cause) {
        super(message, cause);
    }
}
