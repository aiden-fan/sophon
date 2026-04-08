CREATE TABLE IF NOT EXISTS kb_chunks (
  id TEXT PRIMARY KEY,
  source_id TEXT NOT NULL,
  title TEXT,
  uri TEXT,
  content TEXT NOT NULL,
  embedding_json TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_chunks_source_id ON kb_chunks(source_id);

CREATE TABLE IF NOT EXISTS tool_audit_logs (
  id TEXT PRIMARY KEY,
  tool_name TEXT NOT NULL,
  allowed INTEGER NOT NULL,
  reason TEXT,
  latency_ms INTEGER,
  created_at TEXT NOT NULL
);
