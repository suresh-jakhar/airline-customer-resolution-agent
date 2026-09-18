package com.skyair.resolutionagent.model;

public record ChatRequest(
    String customerId,
    String message,
    String sessionId
) {}
