package com.skyair.resolutionagent.model;

import java.time.Instant;
import java.util.List;

public record ActionResponse(
    boolean success,
    String confirmationMessage,
    AllowedAction executedAction,
    List<AllowedAction> remainingAllowedActions,
    Instant timestamp
) {}

