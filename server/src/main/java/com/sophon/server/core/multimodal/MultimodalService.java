package com.sophon.server.core.multimodal;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MultimodalService {

    public String normalizeInput(String message, List<String> modalities) {
        if (modalities == null || modalities.isEmpty()) {
            return message;
        }
        return "[modalities=" + String.join(",", modalities) + "] " + message;
    }
}
