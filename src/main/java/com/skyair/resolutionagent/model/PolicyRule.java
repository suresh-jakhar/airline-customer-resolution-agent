package com.skyair.resolutionagent.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyRule(
    @JsonProperty("id") String id,
    @JsonProperty("category") String category,
    @JsonProperty("applies_to") @JsonAlias({"appliesTo", "flightStatus"}) String appliesTo,
    @JsonProperty("allowed") boolean allowed,
    @JsonProperty("min_delay") @JsonAlias({"min_delay_mins", "minDelayMins"}) int minDelay,
    @JsonProperty("max_inr") @JsonAlias({"max_amount_inr", "maxAmountInr"}) int maxInr,
    @JsonProperty("time_limit") @JsonAlias({"timeLimit", "window"}) String timeLimit,
    @JsonProperty("cash") @JsonAlias({"cash_payout", "cashPayout"}) boolean cash,
    @JsonProperty("escalate") @JsonAlias({"escalate_on_breach", "escalateOnBreach"}) boolean escalate,
    @JsonProperty("summary") @JsonAlias({"rule_summary", "ruleSummary", "detail"}) String summary,
    @JsonProperty("denial_reason") @JsonAlias({"denialReason", "rejectionReason"}) String denialReason
) {
    public PolicyRule(String id, boolean allowed, int minDelay, int maxInr, String timeLimit, boolean cash, boolean escalate, String summary) {
        this(id, "GENERAL", "ALL", allowed, minDelay, maxInr, timeLimit, cash, escalate, summary, "");
    }

    public PolicyRule(boolean allowed, int minDelayMins, int maxAmountInr, String timeLimit, boolean cashPayout, boolean escalateOnBreach, String ruleSummary) {
        this("", "GENERAL", "ALL", allowed, minDelayMins, maxAmountInr, timeLimit, cashPayout, escalateOnBreach, ruleSummary, "");
    }

    public int minDelayMins() {
        return minDelay;
    }

    public int maxAmountInr() {
        return maxInr;
    }

    public boolean cashPayout() {
        return cash;
    }

    public boolean escalateOnBreach() {
        return escalate;
    }

    public String ruleSummary() {
        return summary;
    }

    public boolean appliesToStatus(FlightStatus status) {
        if (appliesTo == null || appliesTo.isBlank() || appliesTo.equalsIgnoreCase("ALL")) return true;
        if (status == null) return false;
        return appliesTo.equalsIgnoreCase(status.name());
    }
}
