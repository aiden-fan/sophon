package com.sophon.core.llm.unified;

import java.util.List;

public record ToolCall(
    String name,
    String argsJson
) { }
