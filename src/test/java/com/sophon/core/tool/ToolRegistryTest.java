package com.sophon.core.tool;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ToolRegistryTest {

    @Test(expected = IllegalStateException.class)
    public void shouldRejectDuplicateToolNames() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new DummyTool("dup"));
        registry.register(new DummyTool("dup"));
    }

    @Test
    public void shouldFilterToolsByName() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new DummyTool("a"));
        registry.register(new DummyTool("b"));

        assertEquals(1, registry.listByNames(java.util.List.of("b")).size());
        assertEquals("b", registry.listByNames(java.util.List.of("b")).get(0).name());
    }

    private record DummyTool(String name) implements Tool {
        @Override public String description() { return "d"; }
        @Override public String parametersSchema() { return "{}"; }
        @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.ok("ok"); }
    }
}
