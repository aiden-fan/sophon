package com.sophon.server.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YamlEnvSubstitutorTest {

    @Test
    void substitutesEnvInString() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("k", "x-${TEST_SOPHON_ENV_SUB}-y");
        try {
            System.setProperty("TEST_SOPHON_ENV_SUB", "v");
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) YamlEnvSubstitutor.substitute(root);
            assertEquals("x-v-y", out.get("k"));
        } finally {
            System.clearProperty("TEST_SOPHON_ENV_SUB");
        }
    }
}
