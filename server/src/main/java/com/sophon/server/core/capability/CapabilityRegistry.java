package com.sophon.server.core.capability;

import com.sophon.common.dto.CapabilityInfo;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CapabilityRegistry {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @PostConstruct
    void initDefaults() {
        register("tool:echo", CapabilityType.TOOL, "Echo tool");
        register("tool:time", CapabilityType.TOOL, "Current server time");
        register("skill:summarize", CapabilityType.SKILL, "Summarize text by truncation");
        register("mcp:placeholder", CapabilityType.MCP_TOOL, "Reserved MCP capability");
        register("plugin:placeholder", CapabilityType.PLUGIN, "Reserved plugin capability");
    }

    public void register(String id, CapabilityType type, String description) {
        entries.put(id, new Entry(id, type, description));
    }

    public List<CapabilityInfo> list() {
        List<CapabilityInfo> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            out.add(new CapabilityInfo(e.id, e.type.name(), e.description));
        }
        return out;
    }

    public boolean exists(String id) {
        return entries.containsKey(id);
    }

    private record Entry(String id, CapabilityType type, String description) {}
}
