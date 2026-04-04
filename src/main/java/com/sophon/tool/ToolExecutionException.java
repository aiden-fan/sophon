package com.sophon.tool;

/** 工具执行失败（参数非法、业务错误等）。 */
public class ToolExecutionException extends Exception {

    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
