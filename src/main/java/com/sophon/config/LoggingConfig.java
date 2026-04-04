package com.sophon.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoggingConfig {

    @JsonProperty("level")
    private String level = "INFO";

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level != null ? level : "INFO";
    }
}
