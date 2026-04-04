package com.sophon.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * 注册到模型侧的工具元数据（OpenAI tools[].function 形态）。
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    /** JSON Schema object（{@code type: object, properties: ...}） */
    private final JsonNode parametersSchema;
    private final ToolRisk risk;

    public ToolDefinition(String name, String description, JsonNode parametersSchema) {
        this(name, description, parametersSchema, ToolRisk.STANDARD);
    }

    public ToolDefinition(String name, String description, JsonNode parametersSchema, ToolRisk risk) {
        this.name = Objects.requireNonNull(name, "name").trim();
        if (this.name.isEmpty()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        this.description = description != null ? description : "";
        this.parametersSchema =
                parametersSchema != null ? parametersSchema : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        this.risk = risk != null ? risk : ToolRisk.STANDARD;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonNode getParametersSchema() {
        return parametersSchema;
    }

    public ToolRisk getRisk() {
        return risk;
    }

    public boolean isHighRisk() {
        return risk == ToolRisk.HIGH;
    }
}
