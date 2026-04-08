package com.sophon.server.api.controller;

import com.sophon.common.dto.CapabilityInfo;
import com.sophon.server.core.capability.CapabilityRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/capabilities")
public class CapabilityController {

    private final CapabilityRegistry registry;

    public CapabilityController(CapabilityRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<CapabilityInfo> list() {
        return registry.list();
    }
}
