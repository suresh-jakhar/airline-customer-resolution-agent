package com.skyair.resolutionagent.model;

import java.util.List;

public record Resolution(
    List<AllowedAction> allowedActions,
    List<Intent> deniedIntents,
    boolean escalationRequired,
    EscalationReason escalationReason,
    List<String> policiesApplied,
    String contextSummary
) {}
