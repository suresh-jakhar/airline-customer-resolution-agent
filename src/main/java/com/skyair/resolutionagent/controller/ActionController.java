package com.skyair.resolutionagent.controller;

import com.skyair.resolutionagent.model.ActionRequest;
import com.skyair.resolutionagent.model.ActionResponse;
import com.skyair.resolutionagent.service.AgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/actions")
@CrossOrigin(origins = "*")
public class ActionController {

    private final AgentService agentService;

    public ActionController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/execute")
    public ResponseEntity<ActionResponse> execute(@RequestBody ActionRequest request) {
        ActionResponse response = agentService.executeAction(request);
        return ResponseEntity.ok(response);
    }
}
