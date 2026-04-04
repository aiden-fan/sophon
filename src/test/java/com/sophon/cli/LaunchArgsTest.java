package com.sophon.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchArgsTest {

    @Test
    void parse_smokeAndSystemPrompt() {
        LaunchArgs a = LaunchArgs.parse(new String[] {"--smoke"});
        assertTrue(a.isSmoke());
        assertNull(a.getSystemPromptFromCli());

        LaunchArgs b = LaunchArgs.parse(new String[] {"-s", "hello", "--smoke"});
        assertTrue(b.isSmoke());
        assertEquals("hello", b.getSystemPromptFromCli());

        LaunchArgs c = LaunchArgs.parse(new String[] {"--system-prompt=p2"});
        assertEquals("p2", c.getSystemPromptFromCli());
        assertFalse(c.isSmoke());
    }
}
