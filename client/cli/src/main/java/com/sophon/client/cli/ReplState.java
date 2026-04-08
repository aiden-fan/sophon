package com.sophon.client.cli;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Persisted REPL preferences under {@code ~/.sophon/config/cli-repl-state.json}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReplState(
        String session,
        boolean stream,
        Integer ragTopK,
        Double ragMinScore,
        String baseUrl
) {
    public static ReplState defaults() {
        return new ReplState("main", false, null, null, null);
    }
}
