package com.sophon.tool;

import com.sophon.ai.dto.ToolCall;
import com.sophon.core.session.SessionManager;
import com.sophon.model.SessionCapabilityConfig;
import com.sophon.observability.UsageTracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 根据 {@link ToolCall} 执行本地 Tool：会话禁用、高危确认、限流、审计与用量（阶段 12–13）。
 */
public final class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry registry;
    private final SessionManager sessionManager;
    private final ToolConfirmationGate confirmationGate;
    private final ToolAuditLogger auditLogger;
    private final UsageTracker usageTracker;
    private final SessionToolRateLimiter rateLimiter;

    public ToolExecutor(ToolRegistry registry) {
        this(registry, null, null, null, null, null);
    }

    public ToolExecutor(ToolRegistry registry, SessionManager sessionManager) {
        this(registry, sessionManager, null, null, null, null);
    }

    public ToolExecutor(
            ToolRegistry registry,
            SessionManager sessionManager,
            ToolConfirmationGate confirmationGate,
            ToolAuditLogger auditLogger,
            UsageTracker usageTracker,
            SessionToolRateLimiter rateLimiter) {
        this.registry = registry != null ? registry : ToolRegistry.empty();
        this.sessionManager = sessionManager;
        this.confirmationGate = confirmationGate;
        this.auditLogger = auditLogger;
        this.usageTracker = usageTracker;
        this.rateLimiter = rateLimiter != null ? rateLimiter : new SessionToolRateLimiter();
    }

    public ToolResult execute(ToolCall call) {
        return execute(null, call);
    }

    public ToolResult execute(String sessionId, ToolCall call) {
        SessionCapabilityConfig cap = null;
        if (sessionManager != null && sessionId != null && !sessionId.isBlank()) {
            cap = sessionManager.getSession(sessionId).orElseThrow().getCapabilities();
            if (!cap.allowsTool(call.name())) {
                log.debug("拒绝执行本会话已禁用的工具: {}", call.name());
                recordAudit(sessionId, call, "disabled", false);
                return ToolResult.error("本会话已禁用工具: " + call.name());
            }
        }
        LocalTool tool =
                registry
                        .get(call.name())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "未知工具: " + call.name() + "（未在 ToolRegistry 注册）"));
        ToolDefinition def = tool.definition();
        String argsSummary = ToolArgsSanitizer.forAudit(call.argumentsJson(), 400);

        if (cap != null && cap.getToolRateLimitPerMinute() != null && cap.getToolRateLimitPerMinute() > 0) {
            if (!rateLimiter.tryAcquire(sessionId, cap.getToolRateLimitPerMinute())) {
                recordAudit(sessionId, call, argsSummary + " [rate-limited]", false);
                return ToolResult.error("本会话 Tool 调用过于频繁，请稍后再试");
            }
        }

        if (def.isHighRisk() && cap != null && sessionId != null) {
            if (!ToolArgsSanitizer.isElevatedTrust(cap.getTrustLevel())) {
                ToolConfirmationGate gate =
                        confirmationGate != null ? confirmationGate : (sid, n, s) -> false;
                if (!gate.confirm(sessionId, call.name(), ToolArgsSanitizer.forAudit(call.argumentsJson(), 200))) {
                    recordAudit(sessionId, call, argsSummary + " [rejected]", false);
                    return ToolResult.error("高危工具执行被拒绝（需终端确认或 /trust elevated）");
                }
            }
        }

        ToolExecutionContext.enter(sessionId);
        try {
            ToolResult r = tool.execute(call.argumentsJson());
            log.debug("工具 {} 执行完成 success={}", call.name(), r.success());
            recordAudit(sessionId, call, argsSummary, r.success());
            if (usageTracker != null && sessionId != null && !sessionId.isBlank()) {
                usageTracker.recordToolInvocation(sessionId, call.name());
            }
            return r;
        } catch (ToolExecutionException e) {
            log.warn("工具执行失败: {}", call.name(), e);
            recordAudit(sessionId, call, argsSummary, false);
            return ToolResult.error(e.getMessage());
        } finally {
            ToolExecutionContext.leave();
        }
    }

    private void recordAudit(String sessionId, ToolCall call, String argsSummary, boolean success) {
        if (auditLogger == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        auditLogger.record(sessionId, call.name(), argsSummary, success);
    }
}
