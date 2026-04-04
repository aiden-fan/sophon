package com.sophon.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigManagerTest {

    @Test
    void load_mergesClasspathYamlAndResolvesDatabasePath() throws Exception {
        AppConfig config = ConfigManager.load();
        assertNotNull(config.getSophon());
        assertNotNull(config.getSophon().getDatabase());
        assertFalse(config.getSophon().getDatabase().getPath().isBlank());
        assertNotNull(config.getSophon().getLogging().getLevel());
    }
}
