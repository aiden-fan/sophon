-- 阶段 1 初版 DDL；演进时与迁移工具对齐
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS sessions (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    capabilities_json TEXT
);

CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY NOT NULL,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    sequence_num INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id);
CREATE INDEX IF NOT EXISTS idx_messages_session_seq ON messages(session_id, sequence_num);

-- 阶段 9：知识库向量块（embedding 为 float32 little-endian blob）
CREATE TABLE IF NOT EXISTS kb_chunks (
    kb_name TEXT NOT NULL,
    chunk_id TEXT NOT NULL,
    content TEXT NOT NULL,
    embedding BLOB NOT NULL,
    PRIMARY KEY (kb_name, chunk_id)
);
CREATE INDEX IF NOT EXISTS idx_kb_chunks_kb ON kb_chunks(kb_name);

-- 阶段 12：Tool 审计
CREATE TABLE IF NOT EXISTS tool_audit (
    id TEXT PRIMARY KEY NOT NULL,
    session_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    args_summary TEXT NOT NULL,
    success INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tool_audit_session ON tool_audit(session_id);

-- 阶段 13：用量（近似 token）
CREATE TABLE IF NOT EXISTS usage_stats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt_tokens INTEGER NOT NULL,
    completion_tokens INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_usage_session ON usage_stats(session_id);

-- 阶段 14：能力预设 Profile
CREATE TABLE IF NOT EXISTS profiles (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    capabilities_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

-- 阶段 17：消息全文检索（FTS5）
CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
    content,
    session_id UNINDEXED,
    message_id UNINDEXED,
    tokenize = 'porter unicode61'
);
