package com.skyair.resolutionagent.controller;

import com.skyair.resolutionagent.model.SessionResponse;
import com.skyair.resolutionagent.service.AgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
@CrossOrigin(origins = "*")
public class SessionController {

    private final AgentService agentService;

    public SessionController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable String customerId,
            @RequestParam(required = false) String sessionId) {
        SessionResponse response = agentService.getSession(customerId, sessionId);
        return ResponseEntity.ok(response);
    }
}
