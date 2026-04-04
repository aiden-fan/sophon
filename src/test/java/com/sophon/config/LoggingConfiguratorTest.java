package com.sophon.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfiguratorTest {

    @AfterEach
    void resetSophonLoggerLevel() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext) {
            ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("com.sophon").setLevel(Level.INFO);
        }
    }

    @Test
    void apply_setsSophonLoggerLevel() {
        AppConfig config = AppConfig.defaults();
        config.getSophon().getLogging().setLevel("DEBUG");
        LoggingConfigurator.apply(config);
        assertTrue(LoggerFactory.getILoggerFactory() instanceof LoggerContext);
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertEquals(Level.DEBUG, lc.getLogger("com.sophon").getLevel());
    }
}
