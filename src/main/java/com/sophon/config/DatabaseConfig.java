package com.sophon.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DatabaseConfig {

    @JsonProperty("path")
    private String path = "";

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path != null ? path : "";
    }
}
