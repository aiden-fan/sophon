package com.sophon.core.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophon.model.CapabilityProfile;
import com.sophon.model.Message;
import com.sophon.model.MessageSearchHit;
import com.sophon.model.MessageRole;
import com.sophon.model.Session;
import com.sophon.model.SessionCapabilityConfig;
import com.sophon.storage.StorageAdapter;
import com.sophon.storage.sqlite.SQLiteStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 会话生命周期与消息的薄封装，不承载 UI；持久化仅经 {@link StorageAdapter}。
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private static final ObjectMapper EXPORT_JSON = new ObjectMapper();

    private final StorageAdapter storage;

    public SessionManager(StorageAdapter storage) {
        this.storage = storage;
    }

    public Session createSession(String title) {
        Instant now = Instant.now();
        String id = SQLiteStorage.newId();
        Session s = new Session(id, title, now, now);
        s.setCapabilities(SessionCapabilityConfig.defaultNew());
        storage.upsertSession(s);
        log.debug("创建会话 id={} title={}", id, title);
        return s;
    }

    public Optional<Session> getSession(String id) {
        return storage.findSessionById(id);
    }

    public Session requireSession(String sessionId) {
        return getSession(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
    }

    public List<Session> listSessions() {
        return storage.listSessions();
    }

    public void updateSessionTitle(String sessionId, String title) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        existing.setTitle(title);
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    public void deleteSession(String sessionId) {
        storage.deleteSession(sessionId);
    }

    public Message appendMessage(String sessionId, MessageRole role, String content) {
        storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        int seq = storage.nextMessageSequence(sessionId);
        Instant now = Instant.now();
        Message m = new Message(SQLiteStorage.newId(), sessionId, role, content, seq, now);
        storage.upsertMessage(m);
        Session s =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalStateException("会话丢失: " + sessionId));
        s.setUpdatedAt(now);
        storage.upsertSession(s);
        log.debug("追加消息 sessionId={} seq={} role={}", sessionId, seq, role);
        return m;
    }

    public List<Message> listMessages(String sessionId) {
        return storage.listMessagesBySessionId(sessionId);
    }

    public Optional<Message> getMessage(String messageId) {
        return storage.findMessageById(messageId);
    }

    public void saveMessagesBatch(List<Message> messages) {
        storage.saveMessagesInTransaction(messages);
    }

    public StorageAdapter storage() {
        return storage;
    }

    /**
     * 覆盖本会话能力配置（整表替换）；用于 application 层与会话级命令。
     */
    public void updateCapabilities(String sessionId, SessionCapabilityConfig capabilities) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        existing.setCapabilities(capabilities != null ? capabilities : SessionCapabilityConfig.defaultNew());
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    /** 将某工具加入或移出本会话禁用列表。 */
    public void setToolDisabled(String sessionId, String toolName, boolean disabled) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        Set<String> next = new HashSet<>(c.getDisabledToolNames());
        if (disabled) {
            next.add(toolName);
        } else {
            next.remove(toolName);
        }
        existing.setCapabilities(c.withDisabledToolNames(next));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    /** 清空本会话对 Tool 的禁用列表（恢复全局可见）。 */
    public void resetSessionToolDisables(String sessionId) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        existing.setCapabilities(c.withDisabledToolNames(Set.of()));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    public void setSkillDisabled(String sessionId, String skillId, boolean disabled) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        Set<String> next = new HashSet<>(c.getDisabledSkillNames());
        if (disabled) {
            next.add(skillId);
        } else {
            next.remove(skillId);
        }
        existing.setCapabilities(c.withDisabledSkillNames(next));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    public void resetSessionSkillDisables(String sessionId) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        existing.setCapabilities(c.withDisabledSkillNames(Set.of()));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    public void attachKnowledgeBase(String sessionId, String kbName) {
        if (kbName == null || kbName.isBlank()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        Set<String> next = new HashSet<>(c.getAttachedKnowledgeBaseNames());
        next.add(kbName.trim());
        existing.setCapabilities(c.withAttachedKnowledgeBaseNames(next));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    public void detachKnowledgeBase(String sessionId, String kbName) {
        if (kbName == null || kbName.isBlank()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        Set<String> next = new HashSet<>(c.getAttachedKnowledgeBaseNames());
        next.remove(kbName.trim());
        existing.setCapabilities(c.withAttachedKnowledgeBaseNames(next));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    public void resetSessionKnowledgeBases(String sessionId) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        existing.setCapabilities(c.withAttachedKnowledgeBaseNames(Set.of()));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    public void setCitationsEnabled(String sessionId, boolean enabled) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        existing.setCapabilities(c.withCitationsEnabled(enabled));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    public void setTrustLevel(String sessionId, String trustLevel) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        existing.setCapabilities(c.withTrustLevel(trustLevel));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    public void setToolRateLimitPerMinute(String sessionId, Integer limitOrNull) {
        Session existing =
                storage.findSessionById(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        SessionCapabilityConfig c = existing.getCapabilities();
        existing.setCapabilities(c.withToolRateLimitPerMinute(limitOrNull));
        existing.setUpdatedAt(Instant.now());
        storage.upsertSession(existing);
    }

    /** 阶段 14：列出已保存的能力预设。 */
    public List<CapabilityProfile> listCapabilityProfiles() {
        return storage.listCapabilityProfiles();
    }

    /** 将当前会话能力快照保存为预设（覆盖同 id）。 */
    public void saveCapabilityProfile(String profileId, String displayName, String sessionId) {
        if (profileId == null || profileId.isBlank() || profileId.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Profile id 须非空且不含空白");
        }
        Session s = requireSession(sessionId);
        storage.upsertCapabilityProfile(
                profileId.trim(),
                displayName != null ? displayName.trim() : "",
                s.getCapabilities().toJson(),
                Instant.now().toEpochMilli());
    }

    /** 将预设中的能力配置应用到本会话。 */
    public void applyCapabilityProfile(String sessionId, String profileId) {
        requireSession(sessionId);
        CapabilityProfile p =
                storage.findCapabilityProfile(profileId).orElseThrow(() -> new IllegalArgumentException("Profile 不存在: " + profileId));
        updateCapabilities(sessionId, p.resolvedCapabilities());
    }

    public void deleteCapabilityProfile(String profileId) {
        storage.deleteCapabilityProfile(profileId);
    }

    /** 导出本会话为 JSON（含能力与消息列表）。 */
    public String exportSessionToJson(String sessionId) {
        try {
            Session s = requireSession(sessionId);
            List<Message> messages = listMessages(sessionId);
            ObjectNode root = EXPORT_JSON.createObjectNode();
            root.put("sophon_export_v1", true);
            ObjectNode sess = root.putObject("session");
            sess.put("title", s.getTitle() != null ? s.getTitle() : "");
            sess.set("capabilities", EXPORT_JSON.readTree(s.getCapabilities().toJson()));
            ArrayNode arr = root.putArray("messages");
            for (Message m : messages) {
                ObjectNode o = arr.addObject();
                o.put("role", m.getRole().name());
                o.put("content", m.getContent() != null ? m.getContent() : "");
                o.put("sequence", m.getSequence());
                o.put("createdAt", m.getCreatedAt().toEpochMilli());
            }
            return EXPORT_JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("exportSessionToJson", e);
        }
    }

    /**
     * 自导出 JSON 新建会话并写入消息（新消息 id 与序号按导出顺序重分配）。
     *
     * @throws IllegalArgumentException 格式非法或解析失败
     */
    public Session importSessionFromJson(String json) {
        try {
            JsonNode root = EXPORT_JSON.readTree(json);
            if (!root.path("sophon_export_v1").asBoolean(false)) {
                throw new IllegalArgumentException("非 Sophon 导出格式（缺少 sophon_export_v1）");
            }
            JsonNode sess = root.required("session");
            String title = sess.path("title").asText("imported");
            SessionCapabilityConfig cap;
            JsonNode capNode = sess.get("capabilities");
            if (capNode != null && !capNode.isNull()) {
                cap = EXPORT_JSON.treeToValue(capNode, SessionCapabilityConfig.class);
            } else {
                cap = SessionCapabilityConfig.defaultNew();
            }
            Session created = createSession(title.isBlank() ? "imported" : title);
            updateCapabilities(created.getId(), cap);
            JsonNode arr = root.path("messages");
            if (!arr.isArray() || arr.isEmpty()) {
                return getSession(created.getId()).orElseThrow();
            }
            List<JsonNode> nodes = new ArrayList<>();
            arr.forEach(nodes::add);
            nodes.sort(Comparator.comparingInt(n -> n.path("sequence").asInt(0)));
            List<Message> batch = new ArrayList<>();
            int seq = 1;
            for (JsonNode n : nodes) {
                MessageRole role = MessageRole.valueOf(n.path("role").asText("USER"));
                String content = n.path("content").asText("");
                long createdMs = n.path("createdAt").asLong(Instant.now().toEpochMilli());
                batch.add(
                        new Message(
                                SQLiteStorage.newId(),
                                created.getId(),
                                role,
                                content,
                                seq++,
                                Instant.ofEpochMilli(createdMs)));
            }
            saveMessagesBatch(batch);
            return getSession(created.getId()).orElseThrow();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("导入 JSON 失败: " + e.getMessage(), e);
        }
    }

    /** 复制配置与全部消息到新会话（新消息 id，序号与原顺序一致）。 */
    public Session forkSession(String fromSessionId, String newTitleOrNull) {
        Session src = requireSession(fromSessionId);
        String title =
                newTitleOrNull == null || newTitleOrNull.isBlank()
                        ? (src.getTitle() != null ? src.getTitle() : "fork") + " (分支)"
                        : newTitleOrNull.trim();
        Session nu = createSession(title);
        updateCapabilities(nu.getId(), src.getCapabilities());
        List<Message> old = listMessages(fromSessionId);
        if (old.isEmpty()) {
            return getSession(nu.getId()).orElseThrow();
        }
        List<Message> copy = new ArrayList<>(old.size());
        for (Message m : old) {
            copy.add(
                    new Message(
                            SQLiteStorage.newId(),
                            nu.getId(),
                            m.getRole(),
                            m.getContent() != null ? m.getContent() : "",
                            m.getSequence(),
                            m.getCreatedAt()));
        }
        saveMessagesBatch(copy);
        return getSession(nu.getId()).orElseThrow();
    }

    /**
     * 阶段 15：从最近一条用户消息起删除本会话后续消息（含该用户消息），返回被删用户话正文供重答。
     */
    public Optional<String> truncateFromLastUser(String sessionId) {
        List<Message> list = listMessages(sessionId);
        for (int i = list.size() - 1; i >= 0; i--) {
            Message m = list.get(i);
            if (m.getRole() == MessageRole.USER) {
                storage.deleteMessagesFromSequence(sessionId, m.getSequence());
                return Optional.ofNullable(m.getContent());
            }
        }
        return Optional.empty();
    }

    /** 清空本会话全部消息上下文，保留会话本身与能力配置。 */
    public int clearContextMessages(String sessionId) {
        Session s = requireSession(sessionId);
        List<Message> list = listMessages(sessionId);
        if (list.isEmpty()) {
            return 0;
        }
        storage.deleteMessagesFromSequence(sessionId, 1);
        s.setUpdatedAt(Instant.now());
        storage.upsertSession(s);
        return list.size();
    }

    /** 阶段 17：跨会话消息检索。 */
    public List<MessageSearchHit> searchMessages(String rawQuery, int limit) {
        return storage.searchMessagesFts(rawQuery, limit);
    }
}
