package com.sophon.server.infrastructure.config;

import com.sophon.server.core.llm.DashScopeLlmProperties;
import com.sophon.server.core.knowledge.KnowledgeProperties;
import com.sophon.server.core.tool.ToolSandboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SophonProperties.class,
        DashScopeLlmProperties.class,
        KnowledgeProperties.class,
        ToolSandboxProperties.class
})
public class SophonConfigConfiguration {
}
