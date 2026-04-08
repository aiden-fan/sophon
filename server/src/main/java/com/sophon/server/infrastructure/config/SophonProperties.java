package com.sophon.server.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sophon")
public class SophonProperties {

    private String home = System.getProperty("user.home") + "/.sophon";
    private ConfigHotReload hotReload = new ConfigHotReload();

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public ConfigHotReload getHotReload() {
        return hotReload;
    }

    public void setHotReload(ConfigHotReload hotReload) {
        this.hotReload = hotReload;
    }

    public static class ConfigHotReload {
        private boolean enabled = true;
        private long debounceMs = 500L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDebounceMs() {
            return debounceMs;
        }

        public void setDebounceMs(long debounceMs) {
            this.debounceMs = debounceMs;
        }
    }
}
