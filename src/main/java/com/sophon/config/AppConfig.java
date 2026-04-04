package com.sophon.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 应用级配置根对象，与 {@code application.yml} 中 {@code sophon} 节点对应。
 */
public class AppConfig {

    @JsonProperty("sophon")
    private SophonConfig sophon = new SophonConfig();

    public SophonConfig getSophon() {
        return sophon;
    }

    public void setSophon(SophonConfig sophon) {
        this.sophon = sophon != null ? sophon : new SophonConfig();
    }

    public static AppConfig defaults() {
        AppConfig c = new AppConfig();
        SophonConfig s = new SophonConfig();
        DatabaseConfig d = new DatabaseConfig();
        d.setPath(ConfigPaths.resolveDataPath("${user.home}/.sophon/data/sophon.db"));
        LoggingConfig l = new LoggingConfig();
        l.setLevel("INFO");
        s.setDatabase(d);
        s.setLogging(l);
        s.setAi(new AiConfig());
        s.setAgent(new AgentConfig());
        c.setSophon(s);
        return c;
    }
}
