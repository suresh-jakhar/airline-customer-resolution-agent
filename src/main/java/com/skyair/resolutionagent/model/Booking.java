package com.skyair.resolutionagent.model;

public record Booking(
    String pnr,
    String customerId,
    Flight outboundFlight,
    Flight returnFlight
) {}
