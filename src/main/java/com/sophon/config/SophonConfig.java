package com.sophon.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SophonConfig {

    @JsonProperty("database")
    private DatabaseConfig database = new DatabaseConfig();

    @JsonProperty("logging")
    private LoggingConfig logging = new LoggingConfig();

    @JsonProperty("ai")
    private AiConfig ai = new AiConfig();

    @JsonProperty("agent")
    private AgentConfig agent = new AgentConfig();

    @JsonProperty("mcp")
    private McpConfig mcp = new McpConfig();

    @JsonProperty("context")
    private ContextConfig context = new ContextConfig();

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database != null ? database : new DatabaseConfig();
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging != null ? logging : new LoggingConfig();
    }

    public AiConfig getAi() {
        return ai;
    }

    public void setAi(AiConfig ai) {
        this.ai = ai != null ? ai : new AiConfig();
    }

    public AgentConfig getAgent() {
        return agent;
    }

    public void setAgent(AgentConfig agent) {
        this.agent = agent != null ? agent : new AgentConfig();
    }

    public McpConfig getMcp() {
        return mcp;
    }

    public void setMcp(McpConfig mcp) {
        this.mcp = mcp != null ? mcp : new McpConfig();
    }

    public ContextConfig getContext() {
        return context;
    }

    public void setContext(ContextConfig context) {
        this.context = context != null ? context : new ContextConfig();
    }
}
