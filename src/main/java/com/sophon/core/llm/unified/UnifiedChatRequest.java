package com.sophon.core.llm.unified;

import java.util.List;

public record UnifiedChatRequest(
    String model,
    List<UnifiedMessage> messages,
    double temperature,
    int maxTokens,
    List<UnifiedTool> tools
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model = "mock";
        private List<UnifiedMessage> messages = List.of();
        private double temperature = 0.8;
        private int maxTokens = 4096;
        private List<UnifiedTool> tools = List.of();

        public Builder model(String model) { this.model = model; return this; }
        public Builder messages(List<UnifiedMessage> messages) { this.messages = messages; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder tools(List<UnifiedTool> tools) { this.tools = tools; return this; }
        public UnifiedChatRequest build() {
            return new UnifiedChatRequest(model, messages, temperature, maxTokens, tools);
        }
    }
}
