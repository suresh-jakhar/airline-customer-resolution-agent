package com.skyair.resolutionagent.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record Flight(
    String flightNumber,
    String origin,
    String destination,
    LocalDate date,
    LocalTime scheduledDeparture,
    FlightStatus status,
    int delayMinutes,
    LocalTime newDeparture
) {}
