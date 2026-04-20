package com.sophon.core.pipeline;

import com.sophon.core.context.ContextBuilder;
import com.sophon.core.init.FrontmatterParser;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.llm.unified.UnifiedChatRequest;
import com.sophon.core.llm.unified.UnifiedChatResponse;
import com.sophon.core.llm.unified.UnifiedMessage;
import com.sophon.core.llm.unified.UnifiedStreamEvent;
import com.sophon.core.selector.DocumentMeta;
import com.sophon.core.selector.DocumentSelector;
import com.sophon.core.selector.SelectionResult;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.ToolResult;
import com.sophon.core.tool.builtin.ReadDocumentsTool;
import com.sophon.core.tool.builtin.WriteChapterTool;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 创作结果
 */
public record ChapterResult(
    String content,
    String filePath
) { }
