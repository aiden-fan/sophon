package com.sophon.core.pipeline;

import com.sophon.core.context.ContextBuilder;
import com.sophon.core.llm.LLMProvider;
import com.sophon.core.llm.unified.*;
import com.sophon.core.selector.DocumentSelector;
import com.sophon.core.selector.SelectionResult;
import com.sophon.core.tool.NovelProjectPath;
import com.sophon.core.tool.Tool;
import com.sophon.core.tool.ToolRegistry;
import com.sophon.core.tool.ToolResult;
import org.junit.Test;
import org.reactivestreams.Publisher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CreationPipelineTest {

    @Test
    public void shouldExecuteAllToolCallsInRound() throws Exception {
        Path root = Files.createTempDirectory("sophon-pipeline-test");
        Files.writeString(root.resolve("outline.md"), "# outline");
        NovelProjectPath projectPath = new NovelProjectPath(root);

        ToolRegistry registry = new ToolRegistry();
        List<String> invoked = new ArrayList<>();
        registry.register(new NamedTool("tool_a", invoked));
        registry.register(new NamedTool("tool_b", invoked));

        FakeProvider provider = new FakeProvider();
        DocumentSelector selector = (userInstruction, available) -> new SelectionResult(List.of("outline.md"));
        ContextBuilder contextBuilder = (documents, userInstruction, renderer, promptName) ->
            List.of(UnifiedMessage.user(userInstruction));

        CreationPipeline pipeline = new CreationPipeline(projectPath, selector, contextBuilder, provider, registry);
        String result = pipeline.create("outline-base", "写点什么", content -> {});

        assertEquals("最终内容", result);
        assertEquals(List.of("tool_a", "tool_b"), invoked);
        assertTrue(provider.lastRequestMessages().stream().anyMatch(m -> m.role() == UnifiedRole.TOOL));
    }

    private static class FakeProvider implements LLMProvider {
        private int round = 0;
        private List<UnifiedMessage> lastRequestMessages = List.of();

        @Override
        public UnifiedChatResponse complete(UnifiedChatRequest request) {
            this.lastRequestMessages = request.messages();
            if (round++ == 0) {
                return new UnifiedChatResponse(
                    "",
                    List.of(
                        new ToolCall("call-1", "tool_a", "{\"k\":\"v\"}"),
                        new ToolCall("call-2", "tool_b", "{\"k\":\"v\"}")
                    ),
                    null,
                    "tool_calls"
                );
            }
            return UnifiedChatResponse.textOnly("最终内容");
        }

        @Override
        public Publisher<UnifiedStreamEvent> stream(UnifiedChatRequest request) {
            return subscriber -> {};
        }

        @Override
        public String providerName() {
            return "fake";
        }

        public List<UnifiedMessage> lastRequestMessages() {
            return lastRequestMessages;
        }
    }

    private record NamedTool(String name, List<String> invoked) implements Tool {
        @Override public String description() { return "d"; }
        @Override public String parametersSchema() { return "{}"; }

        @Override
        public ToolResult execute(Map<String, Object> args) {
            invoked.add(name);
            return ToolResult.ok("ok-" + name);
        }
    }
}
