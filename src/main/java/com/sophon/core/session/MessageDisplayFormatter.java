package com.sophon.core.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophon.ai.dto.ToolCall;
import com.sophon.model.Message;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 将持久化的 {@link Message} 转为 CLI / Web 展示的纯文本（与发往模型的 JSON 存储格式解耦）。
 */
public final class MessageDisplayFormatter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MessageDisplayFormatter() {}

    public static String apiRole(Message m) {
        if (m == null || m.getRole() == null) {
            return "user";
        }
        return switch (m.getRole()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            case SYSTEM -> "system";
        };
    }

    /** 用于终端或 JSON 的「正文」一行/多行展示。 */
    public static String formatContent(Message m) {
        if (m == null) {
            return "";
        }
        String c = m.getContent() != null ? m.getContent() : "";
        return switch (m.getRole()) {
            case USER, SYSTEM -> c;
            case TOOL -> formatToolBody(c);
            case ASSISTANT -> formatAssistantBody(c);
            default -> c;
        };
    }

    private static String formatToolBody(String content) {
        int nl = content.indexOf('\n');
        if (nl > 0) {
            return content.substring(nl + 1).trim();
        }
        return content.trim();
    }

    private static String formatAssistantBody(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String t = content.trim();
        if (t.startsWith("{") && t.contains("sophon_tool_calls_v1")) {
            try {
                List<ToolCall> calls = ConversationManager.parseAssistantToolCalls(content);
                if (calls.isEmpty()) {
                    return "[请求调用工具]";
                }
                return "[工具调用] "
                        + calls.stream().map(ToolCall::name).distinct().collect(Collectors.joining(", "));
            } catch (Exception e) {
                return content;
            }
        }
        if (t.startsWith("{") && t.contains("sophon_assistant_v1")) {
            try {
                JsonNode root = JSON.readTree(content);
                String thinking = root.path("thinking").asText("");
                String body = root.path("content").asText("");
                if (thinking != null && !thinking.isBlank()) {
                    return "〈思考〉\n" + thinking + "\n\n〈正文〉\n" + (body != null ? body : "");
                }
                return body != null ? body : "";
            } catch (Exception e) {
                return content;
            }
        }
        return content;
    }
}
