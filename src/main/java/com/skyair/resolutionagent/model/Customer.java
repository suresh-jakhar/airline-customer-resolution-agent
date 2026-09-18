package com.skyair.resolutionagent.model;

public record Customer(
    String id,
    String name,
    LoyaltyTier loyaltyTier,
    String bookingReference,
    String email,
    String phone,
    int flightCount,
    String priorComplaintSummary
) {}
