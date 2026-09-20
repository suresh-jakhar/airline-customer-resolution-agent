package com.skyair.resolutionagent.controller;

import com.skyair.resolutionagent.service.PolicyConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/policies")
@CrossOrigin(origins = "*")
public class PolicyController {

    private final PolicyConfigService policyConfigService;

    public PolicyController(PolicyConfigService policyConfigService) {
        this.policyConfigService = policyConfigService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPolicies() {
        return ResponseEntity.ok(Map.of(
            "policies", policyConfigService.getPolicies(),
            "actions", policyConfigService.getActionMetadata()
        ));
    }
}
