package com.skyair.resolutionagent.model;

public record ActionRequest(
    String sessionId,
    String customerId,
    AllowedAction actionType
) {}
