package com.sophon.storage;

import com.sophon.model.CapabilityProfile;
import com.sophon.model.Message;
import com.sophon.model.MessageSearchHit;
import com.sophon.model.Session;

import java.util.List;
import java.util.Optional;

/**
 * 持久化端口：会话与消息的 CRUD；具体 JDBC/SQLite 细节由实现类负责。
 */
public interface StorageAdapter extends AutoCloseable {

    void initialize();

    /** 插入或更新会话（按 id 存在则更新元数据与时间戳）。 */
    void upsertSession(Session session);

    Optional<Session> findSessionById(String id);

    List<Session> listSessions();

    void deleteSession(String id);

    void upsertMessage(Message message);

    Optional<Message> findMessageById(String id);

    List<Message> listMessagesBySessionId(String sessionId);

    void deleteMessage(String id);

    /**
     * 在同一事务内写入多条消息（如同会话批量追加）。
     */
    void saveMessagesInTransaction(List<Message> messages);

    /** 会话内下一条消息的序号（从 1 起）。 */
    int nextMessageSequence(String sessionId);

    /** 删除本会话中序号 ≥ {@code fromSequenceInclusive} 的消息（用于编辑后重答等）。 */
    void deleteMessagesFromSequence(String sessionId, int fromSequenceInclusive);

    /** 阶段 14：保存或覆盖命名能力预设。 */
    void upsertCapabilityProfile(String id, String name, String capabilitiesJson, long createdAtEpochMs);

    Optional<CapabilityProfile> findCapabilityProfile(String id);

    List<CapabilityProfile> listCapabilityProfiles();

    void deleteCapabilityProfile(String id);

    /** 阶段 17：跨会话 FTS 检索（关键词经实现层规范化）。 */
    List<MessageSearchHit> searchMessagesFts(String rawQuery, int limit);

    @Override
    void close();
}
