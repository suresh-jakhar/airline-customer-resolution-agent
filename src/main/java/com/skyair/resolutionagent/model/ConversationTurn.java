package com.skyair.resolutionagent.model;

import java.time.Instant;

public record ConversationTurn(
    Instant timestamp,
    String role,
    String message,
    Intent detectedIntent,
    Resolution resolution,
    AllowedAction executedAction
) {}
