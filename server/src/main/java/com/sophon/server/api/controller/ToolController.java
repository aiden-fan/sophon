package com.sophon.server.api.controller;

import com.sophon.server.core.tool.ToolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {

    private final ToolService toolService;

    public ToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping
    public List<String> list() {
        return List.of("echo", "time");
    }

    @PostMapping("/{toolName}/execute")
    public Map<String, Object> execute(@PathVariable("toolName") String toolName, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> input = body == null ? Map.of() : body;
        return Map.of("tool", toolName, "result", toolService.execute(toolName, input));
    }
}
