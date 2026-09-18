package com.skyair.resolutionagent.model;

import java.util.List;

public record SessionResponse(
    Customer customer,
    Booking booking,
    List<ConversationTurn> conversationHistory,
    String sessionId
) {}
