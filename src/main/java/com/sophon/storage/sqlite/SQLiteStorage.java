package com.sophon.storage.sqlite;

import com.sophon.model.CapabilityProfile;
import com.sophon.model.Message;
import com.sophon.model.MessageSearchHit;
import com.sophon.model.MessageRole;
import com.sophon.model.Session;
import com.sophon.model.SessionCapabilityConfig;
import com.sophon.storage.StorageAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * SQLite 实现的 {@link StorageAdapter}；连接与 DDL 封装在本类内。
 */
public class SQLiteStorage implements StorageAdapter {

    private static final Logger log = LoggerFactory.getLogger(SQLiteStorage.class);

    private final Path databaseFile;
    private Connection connection;

    public SQLiteStorage(Path databaseFile) {
        this.databaseFile = databaseFile;
    }

    @Override
    public void initialize() {
        try {
            Path parent = databaseFile.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            runSchemaScript();
            ensureCapabilitiesColumn();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("SQLite 初始化失败: " + databaseFile, e);
        }
    }

    private void runSchemaScript() throws SQLException, IOException {
        try (InputStream in = SQLiteStorage.class.getClassLoader().getResourceAsStream("db/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("classpath 缺少 db/schema.sql");
            }
            String sql = readAll(in);
            List<String> stmts = splitStatements(sql);
            try (Statement st = connection.createStatement()) {
                for (String stmt : stmts) {
                    if (!stmt.isEmpty()) {
                        st.execute(stmt);
                    }
                }
            }
            log.debug("SQLite schema 已应用：{}，语句数={}", databaseFile.toAbsolutePath(), stmts.size());
        }
    }

    static String readAll(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : sql.split("\n")) {
            String t = line.trim();
            if (t.startsWith("--")) {
                continue;
            }
            cur.append(line).append('\n');
            if (t.endsWith(";")) {
                String s = cur.toString().trim();
                if (!s.isEmpty()) {
                    out.add(s.substring(0, s.length() - 1).trim());
                }
                cur.setLength(0);
            }
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) {
            out.add(tail);
        }
        return out;
    }

    private void ensureCapabilitiesColumn() throws SQLException {
        boolean has = false;
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(sessions)")) {
            while (rs.next()) {
                if ("capabilities_json".equalsIgnoreCase(rs.getString("name"))) {
                    has = true;
                    break;
                }
            }
        }
        if (!has) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE sessions ADD COLUMN capabilities_json TEXT");
            }
        }
    }

    @Override
    public void upsertSession(Session session) {
        String sql =
                "INSERT INTO sessions (id, title, created_at, updated_at, capabilities_json) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET title = excluded.title, updated_at = excluded.updated_at, "
                        + "capabilities_json = excluded.capabilities_json";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, session.getId());
            ps.setString(2, session.getTitle());
            ps.setLong(3, session.getCreatedAt().toEpochMilli());
            ps.setLong(4, session.getUpdatedAt().toEpochMilli());
            ps.setString(5, session.getCapabilities().toJson());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("upsertSession", e);
        }
    }

    @Override
    public Optional<Session> findSessionById(String id) {
        String sql = "SELECT id, title, created_at, updated_at, capabilities_json FROM sessions WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSession(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findSessionById", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Session> listSessions() {
        String sql = "SELECT id, title, created_at, updated_at, capabilities_json FROM sessions ORDER BY updated_at DESC";
        List<Session> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapSession(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listSessions", e);
        }
        return list;
    }

    @Override
    public void deleteSession(String id) {
        try {
            try (PreparedStatement ps =
                    connection.prepareStatement("DELETE FROM messages_fts WHERE session_id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            String sql = "DELETE FROM sessions WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("deleteSession", e);
        }
    }

    @Override
    public void upsertMessage(Message message) {
        String sql =
                "INSERT INTO messages (id, session_id, role, content, sequence_num, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET "
                        + "session_id = excluded.session_id, role = excluded.role, "
                        + "content = excluded.content, sequence_num = excluded.sequence_num, created_at = excluded.created_at";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindMessage(ps, message);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("upsertMessage", e);
        }
        replaceMessageFts(message);
    }

    private static void bindMessage(PreparedStatement ps, Message message) throws SQLException {
        ps.setString(1, message.getId());
        ps.setString(2, message.getSessionId());
        ps.setString(3, message.getRole().name());
        ps.setString(4, message.getContent());
        ps.setInt(5, message.getSequence());
        ps.setLong(6, message.getCreatedAt().toEpochMilli());
    }

    @Override
    public Optional<Message> findMessageById(String id) {
        String sql =
                "SELECT id, session_id, role, content, sequence_num, created_at FROM messages WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMessage(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findMessageById", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Message> listMessagesBySessionId(String sessionId) {
        String sql =
                "SELECT id, session_id, role, content, sequence_num, created_at FROM messages "
                        + "WHERE session_id = ? ORDER BY sequence_num ASC";
        List<Message> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMessage(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listMessagesBySessionId", e);
        }
        return list;
    }

    @Override
    public void deleteMessage(String id) {
        deleteFtsByMessageId(id);
        String sql = "DELETE FROM messages WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("deleteMessage", e);
        }
    }

    @Override
    public void deleteMessagesFromSequence(String sessionId, int fromSequenceInclusive) {
        try {
            String delFts =
                    "DELETE FROM messages_fts WHERE message_id IN "
                            + "(SELECT id FROM messages WHERE session_id = ? AND sequence_num >= ?)";
            try (PreparedStatement ps = connection.prepareStatement(delFts)) {
                ps.setString(1, sessionId);
                ps.setInt(2, fromSequenceInclusive);
                ps.executeUpdate();
            }
            String delMsg = "DELETE FROM messages WHERE session_id = ? AND sequence_num >= ?";
            try (PreparedStatement ps = connection.prepareStatement(delMsg)) {
                ps.setString(1, sessionId);
                ps.setInt(2, fromSequenceInclusive);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("deleteMessagesFromSequence", e);
        }
    }

    private void deleteFtsByMessageId(String messageId) {
        try (PreparedStatement ps =
                connection.prepareStatement("DELETE FROM messages_fts WHERE message_id = ?")) {
            ps.setString(1, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("deleteFtsByMessageId", e);
        }
    }

    private void replaceMessageFts(Message m) {
        deleteFtsByMessageId(m.getId());
        String content = m.getContent() != null ? m.getContent() : "";
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO messages_fts (content, session_id, message_id) VALUES (?, ?, ?)")) {
            ps.setString(1, content);
            ps.setString(2, m.getSessionId());
            ps.setString(3, m.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("replaceMessageFts", e);
        }
    }

    @Override
    public void saveMessagesInTransaction(List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        try {
            connection.setAutoCommit(false);
            try {
                String sql =
                        "INSERT INTO messages (id, session_id, role, content, sequence_num, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?) "
                                + "ON CONFLICT(id) DO UPDATE SET "
                                + "session_id = excluded.session_id, role = excluded.role, "
                                + "content = excluded.content, sequence_num = excluded.sequence_num, "
                                + "created_at = excluded.created_at";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (Message m : messages) {
                        bindMessage(ps, m);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                for (Message m : messages) {
                    replaceMessageFts(m);
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("saveMessagesInTransaction", e);
        }
    }

    private static Session mapSession(ResultSet rs) throws SQLException {
        Session s = new Session();
        s.setId(rs.getString("id"));
        s.setTitle(rs.getString("title"));
        s.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        s.setUpdatedAt(Instant.ofEpochMilli(rs.getLong("updated_at")));
        String capJson = rs.getString("capabilities_json");
        s.setCapabilities(SessionCapabilityConfig.fromJson(capJson));
        return s;
    }

    private static Message mapMessage(ResultSet rs) throws SQLException {
        Message m = new Message();
        m.setId(rs.getString("id"));
        m.setSessionId(rs.getString("session_id"));
        m.setRole(MessageRole.valueOf(rs.getString("role")));
        m.setContent(rs.getString("content"));
        m.setSequence(rs.getInt("sequence_num"));
        m.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        return m;
    }

    @Override
    public int nextMessageSequence(String sessionId) {
        String sql = "SELECT COALESCE(MAX(sequence_num), 0) + 1 AS n FROM messages WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("n");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("nextMessageSequence", e);
        }
        return 1;
    }

    /** 阶段 12：Tool 审计落库。 */
    public void insertToolAudit(
            String id, String sessionId, String toolName, String argsSummary, boolean success, long createdAtEpochMs) {
        String sql =
                "INSERT INTO tool_audit (id, session_id, tool_name, args_summary, success, created_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, sessionId);
            ps.setString(3, toolName);
            ps.setString(4, argsSummary != null ? argsSummary : "");
            ps.setInt(5, success ? 1 : 0);
            ps.setLong(6, createdAtEpochMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("insertToolAudit", e);
        }
    }

    /** 阶段 13：用量统计。 */
    public void insertUsageStat(
            String sessionId, String model, int promptTokens, int completionTokens, long createdAtEpochMs) {
        String sql =
                "INSERT INTO usage_stats (session_id, model, prompt_tokens, completion_tokens, created_at) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, model);
            ps.setInt(3, promptTokens);
            ps.setInt(4, completionTokens);
            ps.setLong(5, createdAtEpochMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("insertUsageStat", e);
        }
    }

    public int countToolAuditForSession(String sessionId) {
        String sql = "SELECT COUNT(*) FROM tool_audit WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("countToolAuditForSession", e);
        }
        return 0;
    }

    public int countUsageRowsForSession(String sessionId) {
        String sql = "SELECT COUNT(*) FROM usage_stats WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("countUsageRowsForSession", e);
        }
        return 0;
    }

    /** 供 {@link com.sophon.knowledge.sqlite.SqliteVectorStore} 等与主库共连接的组件使用（须已 {@link #initialize}）。 */
    public Connection jdbcConnection() {
        if (connection == null) {
            throw new IllegalStateException("SQLiteStorage 尚未 initialize");
        }
        return connection;
    }

    /** 生成新的 UUID 字符串主键。 */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void upsertCapabilityProfile(String id, String name, String capabilitiesJson, long createdAtEpochMs) {
        String sql =
                "INSERT INTO profiles (id, name, capabilities_json, created_at) VALUES (?,?,?,?) "
                        + "ON CONFLICT(id) DO UPDATE SET name = excluded.name, "
                        + "capabilities_json = excluded.capabilities_json, created_at = excluded.created_at";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, name != null ? name : "");
            ps.setString(3, capabilitiesJson != null ? capabilitiesJson : "{}");
            ps.setLong(4, createdAtEpochMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("upsertCapabilityProfile", e);
        }
    }

    @Override
    public Optional<CapabilityProfile> findCapabilityProfile(String id) {
        String sql = "SELECT id, name, capabilities_json, created_at FROM profiles WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapProfile(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findCapabilityProfile", e);
        }
        return Optional.empty();
    }

    @Override
    public List<CapabilityProfile> listCapabilityProfiles() {
        String sql = "SELECT id, name, capabilities_json, created_at FROM profiles ORDER BY name COLLATE NOCASE ASC";
        List<CapabilityProfile> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapProfile(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listCapabilityProfiles", e);
        }
        return list;
    }

    @Override
    public void deleteCapabilityProfile(String id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM profiles WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("deleteCapabilityProfile", e);
        }
    }

    private static CapabilityProfile mapProfile(ResultSet rs) throws SQLException {
        return new CapabilityProfile(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("capabilities_json"),
                Instant.ofEpochMilli(rs.getLong("created_at")));
    }

    @Override
    public List<MessageSearchHit> searchMessagesFts(String rawQuery, int limit) {
        int lim = Math.max(1, Math.min(limit, 200));
        String match = toFtsMatchQuery(rawQuery);
        if (match == null) {
            return List.of();
        }
        String sql =
                "SELECT session_id, message_id, content FROM messages_fts WHERE messages_fts MATCH ? LIMIT ?";
        List<MessageSearchHit> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, match);
            ps.setInt(2, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String content = rs.getString("content");
                    String snippet = content != null && content.length() > 200 ? content.substring(0, 200) + "…" : content;
                    out.add(
                            new MessageSearchHit(
                                    rs.getString("session_id"), rs.getString("message_id"), snippet != null ? snippet : ""));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("searchMessagesFts", e);
        }
        return out;
    }

    /** FTS5 MATCH：多词 AND，去特殊字符。 */
    static String toFtsMatchQuery(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        String[] parts = cleaned.split("\\s+");
        StringJoiner sj = new StringJoiner(" AND ");
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            String t = p.length() > 48 ? p.substring(0, 48) : p;
            t = t.replace("\"", "");
            if (!t.isEmpty()) {
                sj.add('"' + t + '"');
            }
        }
        String m = sj.toString();
        return m.isEmpty() ? null : m;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new IllegalStateException("close SQLite", e);
            }
        }
    }
}
