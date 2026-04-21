package com.sophon.cli.command;

import com.sophon.core.context.DefaultContextBuilder;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.pipeline.CreationPipeline;
import com.sophon.core.selector.LlmDocumentSelector;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolRegistry;

public class CreationPipelineFactory {
    private final LLMProvider llm;
    private final ToolRegistry toolRegistry;

    public CreationPipelineFactory(LLMProvider llm, ToolRegistry toolRegistry) {
        this.llm = llm;
        this.toolRegistry = toolRegistry;
    }

    public CreationPipeline create(NovelProjectPath projectPath) {
        var selector = new LlmDocumentSelector(llm, projectPath);
        var contextBuilder = new DefaultContextBuilder();
        return new CreationPipeline(projectPath, selector, contextBuilder, llm, toolRegistry);
    }
}
