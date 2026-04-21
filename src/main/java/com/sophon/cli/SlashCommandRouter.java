package com.sophon.cli;

import com.sophon.config.AiConfig;
import com.sophon.core.application.SessionCapabilityService;
import com.sophon.core.session.ConversationManager;
import com.sophon.core.session.SessionManager;
import com.sophon.model.CapabilityProfile;
import com.sophon.model.MessageSearchHit;
import com.sophon.model.Session;
import com.sophon.model.SessionCapabilityConfig;
import com.sophon.tool.ToolDefinition;
import com.sophon.tool.ToolRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 会话级 {@code /} 命令：不进入对话历史、不调用 LLM；变更经 {@link SessionCapabilityService} 持久化。
 */
public final class SlashCommandRouter {

    /** CLI 识别后切换当前会话并打印历史（见 {@link com.sophon.cli.InteractiveChatCli}）。 */
    public static final String OUTPUT_SESSION_SWITCH_PREFIX = "SOPHON_SESSION_SWITCH:\n";

    private final SessionCapabilityService capabilities;
    private final ConversationManager conversation;
    private final SessionManager sessionManager;
    private final ToolRegistry tools;
    private final AiConfig aiConfig;
    private final Supplier<List<String>> registeredSkillIds;
    private final DoctorService doctorService;

    public SlashCommandRouter(
            SessionCapabilityService capabilities,
            ConversationManager conversation,
            SessionManager sessionManager,
            ToolRegistry tools,
            AiConfig aiConfig,
            Supplier<List<String>> registeredSkillIds,
            DoctorService doctorServiceOrNull) {
        this.capabilities = capabilities;
        this.conversation = conversation;
        this.sessionManager = sessionManager;
        this.tools = tools;
        this.aiConfig = aiConfig;
        this.registeredSkillIds = registeredSkillIds != null ? registeredSkillIds : List::of;
        this.doctorService = doctorServiceOrNull;
    }

    /**
     * @return empty 表示非 {@code /} 命令，应由上层作为普通用户消息交给模型；非 empty 为已处理的反馈文本（含错误提示）。
     */
    public Optional<String> route(String rawLine, String sessionId) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty() || line.charAt(0) != '/') {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>(Arrays.asList(line.split("\\s+")));
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        String head = parts.get(0);
        try {
            return Optional.of(dispatch(head, parts, sessionId));
        } catch (IllegalArgumentException e) {
            return Optional.of("[错误] " + e.getMessage());
        }
    }

    private String dispatch(String head, List<String> parts, String sessionId) {
        sessionManager.requireSession(sessionId);
        return switch (head) {
            case "/help", "/?" -> helpText();
            case "/session" -> handleSession(parts, sessionId);
            case "/prompt" -> handlePrompt(parts, sessionId);
            case "/tools" -> handleTools(parts, sessionId);
            case "/skills" -> handleSkills(parts, sessionId);
            case "/kb", "/knowledge" -> handleKb(parts, sessionId);
            case "/citations" -> handleCitations(parts, sessionId);
            case "/trust" -> handleTrust(parts, sessionId);
            case "/rate" -> handleRate(parts, sessionId);
            case "/profile" -> handleProfile(parts, sessionId);
            case "/export" -> handleExport(parts, sessionId);
            case "/import" -> handleImport(parts);
            case "/fork" -> handleFork(parts, sessionId);
            case "/doctor" -> handleDoctor();
            case "/init" -> handleInit(parts);
            case "/context" -> handleContext(parts, sessionId);
            case "/clear" -> handleContext(List.of("/context", "clear"), sessionId);
            case "/search" -> handleSearch(parts);
            case "/regenerate" -> handleRegenerate(sessionId);
            default -> "未知命令，输入 /help 查看支持的会话命令。";
        };
    }

    private static String helpText() {
        return String.join(
                "\n",
                "会话命令（不以用户消息记入历史）：",
                "  /help",
                "  /session | /session show | /session list | /session use <id>",
                "  /prompt | /prompt show | /prompt set <一行> | /prompt clear",
                "  /tools | /tools all | /tools enable <id> | /tools disable <id> | /tools reset",
                "  /skills | /skills all | /skills enable <id> | /skills disable <id> | /skills reset",
                "  /kb | /kb attach <名称> | /kb detach <名称> | /kb reset",
                "  /citations on | /citations off",
                "  /trust standard | /trust elevated",
                "  /rate tool <每分钟上限>  （0=不限）",
                "  /profile list | /profile save <id> <名称…> | /profile apply <id> | /profile delete <id>",
                "  /export json",
                "  /import json <文件路径>",
                "  /fork [新标题]",
                "  /doctor",
                "  /init [目录]  （初始化网络小说工作目录模板；默认当前工作目录）",
                "  /context clear  （清空当前会话上下文消息）",
                "  /clear  （等价于 /context clear）",
                "  /search <关键词…>",
                "  /regenerate  （从最近用户消息起清除后续并自动重发该话）",
                "对话：输入不以 / 开头的文字，或不在上述白名单中的 / 命令会得到此提示。");
    }

    private String handleSession(List<String> parts, String sessionId) {
        String sub = parts.size() > 1 ? parts.get(1) : "show";
        if ("list".equalsIgnoreCase(sub)) {
            return formatSessionList();
        }
        if ("use".equalsIgnoreCase(sub) || "resume".equalsIgnoreCase(sub)) {
            if (parts.size() < 3) {
                throw new IllegalArgumentException("/session use <会话id>");
            }
            String target = parts.get(2).trim();
            if (target.isEmpty()) {
                throw new IllegalArgumentException("/session use <会话id>");
            }
            sessionManager.getSession(target).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + target));
            return OUTPUT_SESSION_SWITCH_PREFIX + target;
        }
        if (!"show".equalsIgnoreCase(sub)) {
            return "用法: /session | /session show | /session list | /session use <id>";
        }
        Session s = sessionManager.requireSession(sessionId);
        SessionCapabilityConfig c = capabilities.getCapabilities(sessionId);
        String model = aiConfig.getModel();
        String sysNote =
                conversation
                        .getEffectiveSystemPrompt(sessionId)
                        .map(p -> "system 提示: 已生效（会话覆盖或全局，见 /prompt show）")
                        .orElse("system 提示: 无");
        return String.join(
                "\n",
                "会话 id: " + s.getId(),
                "标题: " + s.getTitle(),
                "模型: " + model + "（全局，会话级覆盖见后续阶段）",
                sysNote,
                "Tool 禁用: "
                        + (c.getDisabledToolNames().isEmpty()
                                ? "（无）"
                                : String.join(", ", c.getDisabledToolNames())),
                "Skill 禁用: "
                        + (c.getDisabledSkillNames().isEmpty()
                                ? "（无）"
                                : String.join(", ", c.getDisabledSkillNames())),
                "知识库: "
                        + (c.getAttachedKnowledgeBaseNames().isEmpty()
                                ? "（未绑定）"
                                : String.join(", ", c.getAttachedKnowledgeBaseNames())),
                "citations: " + (c.isCitationsEnabled() ? "on" : "off"),
                "trust: " + c.getTrustLevel(),
                "Tool 速率: "
                        + (c.getToolRateLimitPerMinute() == null
                                ? "不限"
                                : c.getToolRateLimitPerMinute() + " 次/分钟"));
    }

    private String formatSessionList() {
        List<Session> all = sessionManager.listSessions();
        if (all.isEmpty()) {
            return "（暂无会话）";
        }
        StringBuilder sb = new StringBuilder("会话列表（按更新时间倒序）：\n");
        for (Session s : all) {
            String title = s.getTitle() != null ? s.getTitle() : "";
            sb.append("  · ")
                    .append(s.getId())
                    .append("  ")
                    .append(title.isEmpty() ? "（无标题）" : title)
                    .append("\n");
        }
        sb.append("恢复某会话: /session use <上面完整 id>");
        return sb.toString();
    }

    private String handlePrompt(List<String> parts, String sessionId) {
        String rest =
                parts.size() <= 1
                        ? ""
                        : String.join(" ", parts.subList(1, parts.size())).trim();
        if (rest.isEmpty() || "show".equalsIgnoreCase(rest)) {
            return conversation
                    .getEffectiveSystemPrompt(sessionId)
                    .map(p -> "[system 提示] " + p)
                    .orElse("[system 提示] （未设置，模型请求将不含 system 消息）");
        }
        if ("clear".equalsIgnoreCase(rest) || "reset".equalsIgnoreCase(rest)) {
            conversation.setSessionSystemPrompt(sessionId, null);
            return "[system 提示] 已清除会话覆盖，使用全局配置。";
        }
        if (rest.equalsIgnoreCase("set")) {
            throw new IllegalArgumentException("/prompt set 后需要非空文本");
        }
        if (rest.length() > 4 && rest.substring(0, 4).equalsIgnoreCase("set ")) {
            String p = rest.substring(4).trim();
            if (p.isEmpty()) {
                throw new IllegalArgumentException("/prompt set 后需要非空文本");
            }
            conversation.setSessionSystemPrompt(sessionId, p);
            return "[system 提示] 本会话已更新。";
        }
        return "用法: /prompt | /prompt show | /prompt set <一行文本> | /prompt clear";
    }

    private String handleTools(List<String> parts, String sessionId) {
        if (parts.size() == 1) {
            return formatToolsEnabled(sessionId);
        }
        String sub = parts.get(1).toLowerCase();
        return switch (sub) {
            case "all" -> formatToolsAll(sessionId);
            case "enable" -> {
                if (parts.size() < 3) {
                    throw new IllegalArgumentException("/tools enable <id>");
                }
                String id = parts.get(2);
                capabilities.setToolDisabled(sessionId, id, false);
                yield "已在本会话启用工具: " + id;
            }
            case "disable" -> {
                if (parts.size() < 3) {
                    throw new IllegalArgumentException("/tools disable <id>");
                }
                String id = parts.get(2);
                capabilities.setToolDisabled(sessionId, id, true);
                yield "已在本会话禁用工具: " + id;
            }
            case "reset" -> {
                capabilities.resetSessionToolDisables(sessionId);
                yield "已清除本会话 Tool 禁用列表。";
            }
            default -> "未知 /tools 子命令。用法: /tools | /tools all | /tools enable <id> | /tools disable <id> | /tools reset";
        };
    }

    private String formatToolsEnabled(String sessionId) {
        SessionCapabilityConfig c = capabilities.getCapabilities(sessionId);
        List<String> names =
                tools.definitions().stream()
                        .map(ToolDefinition::getName)
                        .filter(c::allowsTool)
                        .sorted()
                        .collect(Collectors.toList());
        if (names.isEmpty()) {
            return "本会话当前无可用工具（全局未注册或全部被禁用）。";
        }
        return "本会话已启用的工具: " + String.join(", ", names);
    }

    private String formatToolsAll(String sessionId) {
        SessionCapabilityConfig c = capabilities.getCapabilities(sessionId);
        StringBuilder sb = new StringBuilder("全局已注册工具与会话状态:\n");
        for (ToolDefinition d : tools.definitions()) {
            String name = d.getName();
            sb.append("  - ")
                    .append(name)
                    .append(c.allowsTool(name) ? " [启用]" : " [禁用]")
                    .append(" — ")
                    .append(d.getDescription())
                    .append('\n');
        }
        if (tools.definitions().isEmpty()) {
            return "全局未注册任何工具。";
        }
        return sb.toString().trim();
    }

    private String handleSkills(List<String> parts, String sessionId) {
        List<String> reg = registeredSkillIds.get();
        if (parts.size() == 1) {
            return formatSkillsSession(reg, sessionId, false);
        }
        String sub = parts.get(1).toLowerCase();
        return switch (sub) {
            case "all" -> formatSkillsSession(reg, sessionId, true);
            case "enable" -> {
                if (parts.size() < 3) {
                    throw new IllegalArgumentException("/skills enable <id>");
                }
                capabilities.setSkillDisabled(sessionId, parts.get(2), false);
                yield "已在本会话启用技能: " + parts.get(2);
            }
            case "disable" -> {
                if (parts.size() < 3) {
                    throw new IllegalArgumentException("/skills disable <id>");
                }
                capabilities.setSkillDisabled(sessionId, parts.get(2), true);
                yield "已在本会话禁用技能: " + parts.get(2);
            }
            case "reset" -> {
                capabilities.resetSessionSkillDisables(sessionId);
                yield "已清除本会话 Skill 禁用列表。";
            }
            default -> "未知 /skills 子命令。";
        };
    }

    private String formatSkillsSession(List<String> registeredIds, String sessionId, boolean verbose) {
        SessionCapabilityConfig c = capabilities.getCapabilities(sessionId);
        StringBuilder sb = new StringBuilder();
        if (registeredIds.isEmpty()) {
            sb.append("全局已注册技能: （尚无，阶段 8 起可用 SkillLoader 注册）\n");
        } else {
            sb.append("全局已注册技能: ").append(String.join(", ", registeredIds)).append('\n');
        }
        if (verbose) {
            for (String id : registeredIds) {
                sb.append("  - ")
                        .append(id)
                        .append(c.allowsSkill(id) ? " [可用]" : " [本会话禁用]")
                        .append('\n');
            }
        }
        if (!c.getDisabledSkillNames().isEmpty()) {
            sb.append("本会话禁用列表: ").append(String.join(", ", c.getDisabledSkillNames()));
        } else if (verbose && registeredIds.isEmpty()) {
            sb.append("本会话禁用列表: （无）");
        } else if (!verbose && registeredIds.isEmpty()) {
            sb.append("本会话禁用列表: （无）");
        }
        return sb.toString().trim();
    }

    private String handleKb(List<String> parts, String sessionId) {
        if (parts.size() == 1) {
            SessionCapabilityConfig c = capabilities.getCapabilities(sessionId);
            if (c.getAttachedKnowledgeBaseNames().isEmpty()) {
                return "本会话未绑定知识库。使用 /kb attach <名称> 添加；入库可用 API 或后续 Web 上传。";
            }
            return "本会话绑定的知识库: " + String.join(", ", c.getAttachedKnowledgeBaseNames());
        }
        String sub = parts.get(1).toLowerCase();
        return switch (sub) {
            case "attach" -> {
                if (parts.size() < 3) {
                    throw new IllegalArgumentException("/kb attach <名称>");
                }
                String name = String.join(" ", parts.subList(2, parts.size())).trim();
                capabilities.attachKnowledgeBase(sessionId, name);
                yield "已绑定知识库: " + name;
            }
            case "detach" -> {
                if (parts.size() < 3) {
                    throw new IllegalArgumentException("/kb detach <名称>");
                }
                String name = String.join(" ", parts.subList(2, parts.size())).trim();
                capabilities.detachKnowledgeBase(sessionId, name);
                yield "已移除知识库: " + name;
            }
            case "reset" -> {
                capabilities.resetSessionKnowledgeBases(sessionId);
                yield "已清空本会话知识库绑定。";
            }
            default -> "未知 /kb 子命令。用法: /kb | /kb attach <名称> | /kb detach <名称> | /kb reset";
        };
    }

    private String handleTrust(List<String> parts, String sessionId) {
        if (parts.size() < 2) {
            return "用法: /trust standard | /trust elevated";
        }
        String t = parts.get(1).toLowerCase();
        if ("standard".equals(t) || "elevated".equals(t)) {
            capabilities.setTrustLevel(sessionId, t);
            return "信任级别已设为: " + t + "（elevated 时高危 Tool 不再要求终端确认）";
        }
        return "用法: /trust standard | /trust elevated";
    }

    private String handleRate(List<String> parts, String sessionId) {
        if (parts.size() < 3 || !"tool".equalsIgnoreCase(parts.get(1))) {
            return "用法: /rate tool <每分钟上限>  （0 表示不限）";
        }
        int n;
        try {
            n = Integer.parseInt(parts.get(2));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("上限须为整数");
        }
        if (n < 0) {
            throw new IllegalArgumentException("上限不能为负");
        }
        capabilities.setToolRateLimitPerMinute(sessionId, n == 0 ? null : n);
        return n == 0 ? "已取消本会话 Tool 速率限制。" : "本会话 Tool 限制为每分钟 " + n + " 次。";
    }

    private String handleCitations(List<String> parts, String sessionId) {
        if (parts.size() < 2) {
            return "用法: /citations on | /citations off";
        }
        String sub = parts.get(1).toLowerCase();
        if ("on".equals(sub)) {
            capabilities.setCitationsEnabled(sessionId, true);
            return "citations 已开启（RAG 回答将附带可解析的 chunk 引用行）。";
        }
        if ("off".equals(sub)) {
            capabilities.setCitationsEnabled(sessionId, false);
            return "citations 已关闭。";
        }
        return "用法: /citations on | /citations off";
    }

    private String handleProfile(List<String> parts, String sessionId) {
        if (parts.size() < 2) {
            return "用法: /profile list | /profile save <id> <名称…> | /profile apply <id> | /profile delete <id>";
        }
        String sub = parts.get(1).toLowerCase();
        return switch (sub) {
            case "list" -> {
                List<CapabilityProfile> list = sessionManager.listCapabilityProfiles();
                if (list.isEmpty()) {
                    yield "暂无已保存的 Profile。使用 /profile save <id> <名称> 从当前会话快照保存。";
                }
                StringBuilder sb = new StringBuilder("已保存的 Profile:\n");
                for (CapabilityProfile p : list) {
                    sb.append("  - ")
                            .append(p.getId())
                            .append(" — ")
                            .append(p.getName())
                            .append('\n');
                }
                yield sb.toString().trim();
            }
            case "save" -> {
                if (parts.size() < 4) {
                    throw new IllegalArgumentException("/profile save <id> <名称…>");
                }
                String id = parts.get(2);
                String name = String.join(" ", parts.subList(3, parts.size())).trim();
                sessionManager.saveCapabilityProfile(id, name, sessionId);
                yield "已保存 Profile: " + id;
            }
            case "apply" -> {
                if (parts.size() < 3) {
                    throw new IllegalArgumentException("/profile apply <id>");
                }
                sessionManager.applyCapabilityProfile(sessionId, parts.get(2));
                yield "已应用 Profile: " + parts.get(2);
            }
            case "delete" -> {
                if (parts.size() < 3) {
                    throw new IllegalArgumentException("/profile delete <id>");
                }
                sessionManager.deleteCapabilityProfile(parts.get(2));
                yield "已删除 Profile: " + parts.get(2);
            }
            default -> "未知 /profile 子命令。";
        };
    }

    private String handleExport(List<String> parts, String sessionId) {
        if (parts.size() < 2 || !"json".equalsIgnoreCase(parts.get(1))) {
            return "用法: /export json";
        }
        return sessionManager.exportSessionToJson(sessionId);
    }

    private String handleImport(List<String> parts) {
        if (parts.size() < 3 || !"json".equalsIgnoreCase(parts.get(1))) {
            return "用法: /import json <文件路径>";
        }
        Path path = Path.of(String.join(" ", parts.subList(2, parts.size())).trim());
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Session nu = sessionManager.importSessionFromJson(json);
            return "已导入新会话 id=" + nu.getId() + " 标题=" + nu.getTitle();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取或解析文件: " + e.getMessage());
        }
    }

    private String handleFork(List<String> parts, String sessionId) {
        String title = parts.size() <= 1 ? null : String.join(" ", parts.subList(1, parts.size())).trim();
        Session nu = sessionManager.forkSession(sessionId, title);
        return "已分叉新会话 id=" + nu.getId() + " 标题=" + nu.getTitle();
    }

    private String handleDoctor() {
        if (doctorService == null) {
            return "（自检服务未注入）";
        }
        return doctorService.runReport();
    }

    private String handleInit(List<String> parts) {
        String rawPath =
                parts.size() <= 1
                        ? System.getProperty("user.dir", ".")
                        : String.join(" ", parts.subList(1, parts.size())).trim();
        if (rawPath.isEmpty()) {
            throw new IllegalArgumentException("/init [目录]");
        }
        NovelWorkspaceInitializer initializer = new NovelWorkspaceInitializer();
        NovelWorkspaceInitializer.InitResult result = initializer.initialize(Path.of(rawPath));
        return "小说工作目录初始化完成: "
                + result.root()
                + "（新建文件 "
                + result.createdFiles()
                + "，已存在跳过 "
                + result.skippedFiles()
                + "）";
    }

    private String handleContext(List<String> parts, String sessionId) {
        String sub = parts.size() > 1 ? parts.get(1).toLowerCase() : "";
        if (!"clear".equals(sub) && !"reset".equals(sub)) {
            return "用法: /context clear";
        }
        int n = sessionManager.clearContextMessages(sessionId);
        return "已清空当前会话上下文，共删除 " + n + " 条消息。";
    }

    private String handleSearch(List<String> parts) {
        if (parts.size() < 2) {
            throw new IllegalArgumentException("/search <关键词…>");
        }
        String q = String.join(" ", parts.subList(1, parts.size())).trim();
        List<MessageSearchHit> hits = sessionManager.searchMessages(q, 20);
        if (hits.isEmpty()) {
            return "未找到匹配消息。";
        }
        StringBuilder sb = new StringBuilder("检索结果（最多 20 条）：\n");
        for (MessageSearchHit h : hits) {
            sb.append("  session=")
                    .append(h.getSessionId())
                    .append(" msg=")
                    .append(h.getMessageId())
                    .append("\n    ")
                    .append(h.getContentSnippet().replace("\n", " "))
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private String handleRegenerate(String sessionId) {
        return sessionManager
                .truncateFromLastUser(sessionId)
                .map(u -> "SOPHON_REGENERATE:\n" + u)
                .orElse("没有可重答的用户消息。");
    }
}
