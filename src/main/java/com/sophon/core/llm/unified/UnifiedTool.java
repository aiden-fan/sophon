package com.sophon.core.llm.unified;

public record UnifiedTool(
    String name,
    String description,
    String parametersJsonSchema
) { }
