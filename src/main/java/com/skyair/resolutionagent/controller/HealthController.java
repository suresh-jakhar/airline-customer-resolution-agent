package com.skyair.resolutionagent.controller;

import com.skyair.resolutionagent.service.LlmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    private final LlmService llmService;

    public HealthController(LlmService llmService) {
        this.llmService = llmService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "llmConfigured", llmService.isConfigured()
        ));
    }
}
