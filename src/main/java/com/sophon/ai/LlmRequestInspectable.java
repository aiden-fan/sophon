package com.sophon.ai;

import com.sophon.ai.dto.LlmMessage;
import com.sophon.tool.ToolDefinition;

import java.util.List;

/** 供日志系统读取“实际发往模型的请求体”与供应商标识。 */
public interface LlmRequestInspectable {

    String buildRequestPayload(List<LlmMessage> messages, boolean stream, List<ToolDefinition> tools)
            throws AIException;

    default String providerLabel() {
        return this.getClass().getSimpleName();
    }
}
