package com.sophon.bootstrap;

import com.sophon.ai.AIProvider;
import com.sophon.ai.FallbackAiProvider;
import com.sophon.ai.dashscope.DashscopeProvider;
import com.sophon.cli.ConsoleToolConfirmationGate;
import com.sophon.cli.DoctorService;
import com.sophon.cli.SlashCommandRouter;
import com.sophon.config.AppConfig;
import com.sophon.core.agent.AgentEngine;
import com.sophon.core.application.ChatApplicationService;
import com.sophon.core.application.SessionCapabilityService;
import com.sophon.core.session.ConversationManager;
import com.sophon.core.session.SessionManager;
import com.sophon.knowledge.KnowledgeManager;
import com.sophon.knowledge.embedding.HashEmbeddingProvider;
import com.sophon.knowledge.rag.RAGEngine;
import com.sophon.knowledge.sqlite.SqliteVectorStore;
import com.sophon.observability.SqliteToolAuditLogger;
import com.sophon.observability.SqliteUsageTracker;
import com.sophon.skill.SkillExecutor;
import com.sophon.skill.SkillLoader;
import com.sophon.skill.SkillRegistry;
import com.sophon.skill.builtin.NovelWriterSkill;
import com.sophon.skill.model.SkillDefinition;
import com.sophon.storage.sqlite.SQLiteStorage;
import com.sophon.tool.SessionToolRateLimiter;
import com.sophon.tool.ToolExecutor;
import com.sophon.tool.ToolRegistry;
import com.sophon.tool.local.BuildNovelPromptLocalTool;
import com.sophon.tool.local.CreateFileLocalTool;
import com.sophon.tool.local.InvokeSkillLocalTool;
import com.sophon.tool.local.ReadFileLocalTool;
import com.sophon.tool.local.WriteFileLocalTool;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 组装 CLI / Web 共用的运行时依赖（阶段 16 起复用）。
 */
public final class SophonBootstrap {

    public record Handle(
            SQLiteStorage storage,
            SessionManager sessions,
            ConversationManager conversation,
            ToolRegistry tools,
            ToolExecutor toolExec,
            KnowledgeManager knowledge,
            SqliteVectorStore vectorStore,
            RAGEngine ragEngine,
            SkillRegistry skillRegistry,
            AgentEngine agent,
            ChatApplicationService chat,
            SessionCapabilityService capabilityService,
            SlashCommandRouter slashCommands,
            DoctorService doctorService)
            implements AutoCloseable {

        @Override
        public void close() {
            storage.close();
        }
    }

    private SophonBootstrap() {}

    public static Handle launch(AppConfig config, Path dbPath) throws Exception {
        SQLiteStorage storage = new SQLiteStorage(dbPath);
        storage.initialize();
        SessionManager sessions = new SessionManager(storage);
        ConversationManager conversation =
                new ConversationManager(sessions, config.getSophon().getAi(), config.getSophon().getContext());
        AIProvider primary = new DashscopeProvider(config.getSophon().getAi());
        String fb = config.getSophon().getAi().getFallbackModel();
        AIProvider ai =
                fb != null && !fb.isBlank()
                        ? new FallbackAiProvider(primary, new DashscopeProvider(config.getSophon().getAi(), fb))
                        : primary;
        ToolRegistry tools = new ToolRegistry();
        tools.register(new ReadFileLocalTool());
        tools.register(new CreateFileLocalTool());
        tools.register(new WriteFileLocalTool());
        tools.register(new BuildNovelPromptLocalTool());
        ToolExecutor toolExec =
                new ToolExecutor(
                        tools,
                        sessions,
                        new ConsoleToolConfirmationGate(),
                        new SqliteToolAuditLogger(storage),
                        new SqliteUsageTracker(storage),
                        new SessionToolRateLimiter());
        HashEmbeddingProvider embeddings = new HashEmbeddingProvider(64);
        SqliteVectorStore vectorStore = new SqliteVectorStore(storage.jdbcConnection());
        KnowledgeManager knowledge = new KnowledgeManager(embeddings, vectorStore);
        RAGEngine ragEngine = new RAGEngine(knowledge, sessions);
        SkillRegistry skillRegistry = new SkillRegistry();
        SkillLoader skillLoader = new SkillLoader();
        SkillDefinition novelWriterDef =
                skillLoader.loadSkillMdResource(
                        SophonBootstrap.class.getClassLoader(), "skills/novel_writer/SKILL.md");
        skillLoader.validateRequiredTools(novelWriterDef, tools);
        skillRegistry.register(new NovelWriterSkill(novelWriterDef));
        SkillExecutor skillExecutor = new SkillExecutor(sessions, skillRegistry, toolExec);
        Map<String, String> skillDescriptions = new LinkedHashMap<>();
        for (String id : skillRegistry.ids()) {
            String d = skillRegistry.get(id).map(s -> s.definition().getDescription()).orElse("");
            skillDescriptions.put(id, d);
        }
        tools.register(new InvokeSkillLocalTool(skillExecutor, skillRegistry.ids(), skillDescriptions));
        AgentEngine agent =
                new AgentEngine(
                        sessions, conversation, ai, config.getSophon().getAgent(), tools, toolExec, null, ragEngine);
        ChatApplicationService chat = new ChatApplicationService(agent);
        SessionCapabilityService capSvc = new SessionCapabilityService(sessions);
        DoctorService doctor =
                new DoctorService(config, tools, knowledge, vectorStore, storage);
        SlashCommandRouter slash =
                new SlashCommandRouter(
                        capSvc,
                        conversation,
                        sessions,
                        tools,
                        config.getSophon().getAi(),
                        skillRegistry::ids,
                        doctor);
        return new Handle(
                storage,
                sessions,
                conversation,
                tools,
                toolExec,
                knowledge,
                vectorStore,
                ragEngine,
                skillRegistry,
                agent,
                chat,
                capSvc,
                slash,
                doctor);
    }
}
