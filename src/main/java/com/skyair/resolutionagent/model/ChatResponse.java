package com.skyair.resolutionagent.model;

import java.util.List;

public record ChatResponse(
    String agentMessage,
    Resolution resolution,
    String sessionId,
    List<ConversationTurn> updatedHistory,
    Intent detectedIntent,
    String customerId
) {
    public ChatResponse(String agentMessage, Resolution resolution, String sessionId, List<ConversationTurn> updatedHistory, Intent detectedIntent) {
        this(agentMessage, resolution, sessionId, updatedHistory, detectedIntent, null);
    }
}
