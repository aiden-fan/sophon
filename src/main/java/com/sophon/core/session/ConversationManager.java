package com.sophon.core.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophon.ai.dto.LlmMessage;
import com.sophon.ai.dto.ToolCall;
import com.sophon.config.AiConfig;
import com.sophon.config.ContextConfig;
import com.sophon.model.Message;
import com.sophon.model.MessageRole;
import com.sophon.model.Session;
import com.sophon.model.SessionCapabilityConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 会话内消息列表与发往模型的上下文组装；阶段 15 起支持按 {@link ContextConfig} 的字符窗口裁剪（非静默，可带说明）。
 */
public class ConversationManager {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SessionManager sessionManager;
    private final AiConfig aiConfig;
    private final ContextConfig contextConfig;

    public ConversationManager(SessionManager sessionManager, AiConfig aiConfig) {
        this(sessionManager, aiConfig, new ContextConfig());
    }

    public ConversationManager(SessionManager sessionManager, AiConfig aiConfig, ContextConfig contextConfig) {
        this.sessionManager = sessionManager;
        this.aiConfig = aiConfig;
        this.contextConfig = contextConfig != null ? contextConfig : new ContextConfig();
    }

    /**
     * 设置本会话专用 system 提示；{@code null} 或空白表示清除覆盖，恢复为 {@code application.yml} / 环境变量 / 启动参数。
     * 持久化在 {@link com.sophon.model.SessionCapabilityConfig}。
     */
    public void setSessionSystemPrompt(String sessionId, String prompt) {
        Session s =
                sessionManager
                        .getSession(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = s.getCapabilities();
        String next = prompt == null || prompt.isBlank() ? null : prompt.trim();
        sessionManager.updateCapabilities(sessionId, c.withSystemPrompt(next));
    }

    /** 当前会话实际发往模型的 system 文本（含覆盖）。 */
    public Optional<String> getEffectiveSystemPrompt(String sessionId) {
        Session s =
                sessionManager
                        .getSession(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        String o = s.getCapabilities().getSystemPrompt();
        if (o != null && !o.isBlank()) {
            return Optional.of(o.trim());
        }
        String g = aiConfig.getSystemPrompt();
        if (g != null && !g.isBlank()) {
            return Optional.of(g.trim());
        }
        return Optional.empty();
    }

    public void appendUserMessage(String sessionId, String content) {
        sessionManager.appendMessage(sessionId, MessageRole.USER, content);
    }

    public void appendAssistantMessage(String sessionId, String content) {
        sessionManager.appendMessage(sessionId, MessageRole.ASSISTANT, content);
    }

    /**
     * 持久化带「思考 / 最终正文」区分的 assistant 轮次；仅正文参与 {@link #buildMessagesForModel} 中发往模型的 assistant 文本。
     */
    public void appendAssistantSegments(String sessionId, String finalText, String thinkingOrNull) {
        String body = finalText != null ? finalText : "";
        if (thinkingOrNull == null || thinkingOrNull.isBlank()) {
            appendAssistantMessage(sessionId, body);
            return;
        }
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("sophon_assistant_v1", true);
            root.put("thinking", thinkingOrNull);
            root.put("content", body);
            sessionManager.appendMessage(sessionId, MessageRole.ASSISTANT, JSON.writeValueAsString(root));
        } catch (Exception e) {
            throw new IllegalStateException("序列化 assistant 分段失败", e);
        }
    }

    /**
     * 持久化仅含 tool_calls 的 assistant 轮次（JSON），供下一轮组装 {@link LlmMessage#assistantWithToolCalls}。
     */
    public void appendAssistantToolCallsMessage(String sessionId, List<ToolCall> calls) {
        try {
            String payload = serializeAssistantToolCalls(calls);
            sessionManager.appendMessage(sessionId, MessageRole.ASSISTANT, payload);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 tool_calls 失败", e);
        }
    }

    /**
     * 持久化 tool 角色结果；存储格式为首行 {@code toolCallId}，换行后为发给模型的 content。
     */
    public void appendToolMessage(String sessionId, String toolCallId, String toolContent) {
        String body = toolCallId + "\n" + toolContent;
        sessionManager.appendMessage(sessionId, MessageRole.TOOL, body);
    }

    /**
     * 按顺序组装：可选 system 提示 + 库中消息映射为 LLM 角色。
     * {@link MessageRole#TOOL} 映射为 {@code tool}；含 tool_calls 的 assistant 从 JSON 还原。
     */
    public List<LlmMessage> buildMessagesForModel(String sessionId) {
        List<Message> rows = sessionManager.listMessages(sessionId);
        List<LlmMessage> out = new ArrayList<>();
        String sys =
                sessionManager
                        .getSession(sessionId)
                        .map(sess -> sess.getCapabilities().getSystemPrompt())
                        .orElse(null);
        if (sys == null || sys.isBlank()) {
            sys = aiConfig.getSystemPrompt();
        }
        if (sys != null && !sys.isBlank()) {
            out.add(LlmMessage.system(sys.trim()));
        }
        for (Message m : rows) {
            out.add(mapMessage(m));
        }
        return applyContextWindow(out);
    }

    /**
     * 保留前缀连续 {@code system} 消息，自尾部向前填充直至达到 {@link ContextConfig#getMaxChars()}；
     * 0 或负数 max 表示不限制。
     */
    private List<LlmMessage> applyContextWindow(List<LlmMessage> messages) {
        int max = contextConfig.getMaxChars();
        if (max <= 0) {
            return messages;
        }
        int leadingSystemEnd = 0;
        while (leadingSystemEnd < messages.size() && "system".equals(messages.get(leadingSystemEnd).role())) {
            leadingSystemEnd++;
        }
        List<LlmMessage> prefix = messages.subList(0, leadingSystemEnd);
        List<LlmMessage> tail = messages.subList(leadingSystemEnd, messages.size());
        int tailChars = tail.stream().mapToInt(ConversationManager::estimateMessageChars).sum();
        if (tailChars <= max) {
            return messages;
        }
        List<LlmMessage> kept = new ArrayList<>();
        int used = 0;
        for (int i = tail.size() - 1; i >= 0; i--) {
            LlmMessage m = tail.get(i);
            int c = estimateMessageChars(m);
            if (used + c > max && !kept.isEmpty()) {
                break;
            }
            if (used + c > max) {
                kept.add(0, truncateLlmMessage(m, max));
                break;
            }
            kept.add(0, m);
            used += c;
        }
        List<LlmMessage> out = new ArrayList<>(prefix);
        if (contextConfig.isTruncationNotice() && kept.size() < tail.size()) {
            out.add(LlmMessage.system("[Sophon] 较早对话已按上下文窗口（max-chars）省略。"));
        }
        out.addAll(kept);
        return out;
    }

    private static int estimateMessageChars(LlmMessage m) {
        int n = m.content() != null ? m.content().length() : 0;
        if (m.hasToolCalls()) {
            for (ToolCall tc : m.toolCalls()) {
                n += tc.argumentsJson() != null ? tc.argumentsJson().length() : 0;
                n += tc.name() != null ? tc.name().length() : 0;
                n += 32;
            }
        }
        if (m.toolCallId() != null) {
            n += m.toolCallId().length();
        }
        return Math.max(n, 1);
    }

    private static LlmMessage truncateLlmMessage(LlmMessage m, int max) {
        if (m.hasToolCalls()) {
            return m;
        }
        String c = m.content() != null ? m.content() : "";
        if (c.length() <= max) {
            return m;
        }
        return new LlmMessage(m.role(), c.substring(0, Math.max(0, max - 1)) + "…", m.toolCalls(), m.toolCallId());
    }

    static String serializeAssistantToolCalls(List<ToolCall> calls) throws Exception {
        var root = JSON.createObjectNode();
        root.put("sophon_tool_calls_v1", true);
        ArrayNode arr = root.putArray("calls");
        for (ToolCall c : calls) {
            var o = arr.addObject();
            o.put("id", c.id());
            o.put("name", c.name());
            o.put("arguments", c.argumentsJson());
        }
        return JSON.writeValueAsString(root);
    }

    private static LlmMessage mapMessage(Message m) {
        String content = m.getContent();
        if (m.getRole() == MessageRole.ASSISTANT && isAssistantToolCallsJson(content)) {
            try {
                List<ToolCall> calls = parseAssistantToolCalls(content);
                return LlmMessage.assistantWithToolCalls(calls);
            } catch (Exception e) {
                return LlmMessage.assistant(content);
            }
        }
        if (m.getRole() == MessageRole.ASSISTANT && isAssistantStructuredJson(content)) {
            try {
                JsonNode root = JSON.readTree(content);
                String body = root.path("content").asText("");
                return LlmMessage.assistant(body);
            } catch (Exception e) {
                return LlmMessage.assistant(content);
            }
        }
        if (m.getRole() == MessageRole.TOOL) {
            int nl = content.indexOf('\n');
            if (nl > 0) {
                String id = content.substring(0, nl);
                String body = content.substring(nl + 1);
                return LlmMessage.tool(id, body);
            }
            return LlmMessage.tool("unknown", content);
        }
        String role;
        switch (m.getRole()) {
            case USER -> role = "user";
            case ASSISTANT -> role = "assistant";
            case SYSTEM -> role = "system";
            default -> role = "user";
        }
        return new LlmMessage(role, content);
    }

    static boolean isAssistantToolCallsJson(String content) {
        if (content == null) {
            return false;
        }
        String t = content.trim();
        return t.startsWith("{") && t.contains("sophon_tool_calls_v1");
    }

    static boolean isAssistantStructuredJson(String content) {
        if (content == null) {
            return false;
        }
        String t = content.trim();
        return t.startsWith("{") && t.contains("sophon_assistant_v1");
    }

    static List<ToolCall> parseAssistantToolCalls(String content) throws Exception {
        JsonNode root = JSON.readTree(content);
        JsonNode calls = root.path("calls");
        List<ToolCall> out = new ArrayList<>();
        if (calls.isArray()) {
            for (JsonNode c : calls) {
                String id = c.path("id").asText("");
                String name = c.path("name").asText("");
                String args = c.path("arguments").asText("");
                out.add(new ToolCall(id, name, args));
            }
        }
        return out;
    }
}
