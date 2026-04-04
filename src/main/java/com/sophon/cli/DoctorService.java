package com.sophon.cli;

import com.sophon.config.AppConfig;
import com.sophon.knowledge.KnowledgeManager;
import com.sophon.knowledge.sqlite.SqliteVectorStore;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段 17：环境自检（API Key、MCP、向量块、已注册 Tool 等），供 {@code /doctor} 使用。
 */
public final class DoctorService {

    private final AppConfig config;
    private final ToolRegistry tools;
    private final KnowledgeManager knowledgeOrNull;
    private final SqliteVectorStore vectorStoreOrNull;
    private final SQLiteStorage storageOrNull;

    public DoctorService(
            AppConfig config,
            ToolRegistry tools,
            KnowledgeManager knowledgeOrNull,
            SqliteVectorStore vectorStoreOrNull,
            SQLiteStorage storageOrNull) {
        this.config = config != null ? config : AppConfig.defaults();
        this.tools = tools != null ? tools : ToolRegistry.empty();
        this.knowledgeOrNull = knowledgeOrNull;
        this.vectorStoreOrNull = vectorStoreOrNull;
        this.storageOrNull = storageOrNull;
    }

    public String runReport() {
        List<String> lines = new ArrayList<>();
        lines.add("=== Sophon /doctor ===");
        String envKey = System.getenv("DASHSCOPE_API_KEY");
        boolean keyOk = envKey != null && !envKey.isBlank();
        String cfgKey = config.getSophon().getAi().getApiKey();
        if (!keyOk && cfgKey != null && !cfgKey.isBlank()) {
            keyOk = true;
        }
        lines.add("API Key: " + (keyOk ? "已配置（环境变量或配置文件）" : "未检测到（需 DASHSCOPE_API_KEY 或 sophon.ai.api-key）"));
        lines.add("主模型: " + config.getSophon().getAi().getModel());
        String fb = config.getSophon().getAi().getFallbackModel();
        lines.add("备用模型: " + (fb == null || fb.isBlank() ? "（未配置）" : fb));
        lines.add("MCP: " + (config.getSophon().getMcp().isEnabled() ? "enabled" : "disabled"));
        lines.add("已注册 Tool 数: " + tools.definitions().size());
        if (vectorStoreOrNull != null) {
            lines.add("向量库 chunk 总数: " + vectorStoreOrNull.countAllChunks());
        } else {
            lines.add("向量库: （未连接，跳过计数）");
        }
        if (knowledgeOrNull != null) {
            lines.add("KnowledgeManager: 已注入");
        } else {
            lines.add("KnowledgeManager: （未注入）");
        }
        if (storageOrNull != null) {
            lines.add("SQLite: 已连接");
        } else {
            lines.add("SQLite: （未注入）");
        }
        lines.add("上下文窗口 max-chars: " + config.getSophon().getContext().getMaxChars());
        return String.join("\n", lines);
    }
}
