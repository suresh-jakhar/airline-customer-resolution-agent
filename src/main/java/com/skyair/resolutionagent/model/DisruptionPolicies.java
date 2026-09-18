package com.skyair.resolutionagent.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DisruptionPolicies {

    private final Map<String, PolicyRule> rules = new LinkedHashMap<>();

    public DisruptionPolicies() {}

    public DisruptionPolicies(Map<String, PolicyRule> initialRules) {
        if (initialRules != null) {
            this.rules.putAll(initialRules);
        }
    }

    @JsonAnySetter
    public void setRule(String key, PolicyRule rule) {
        if (key != null && rule != null) {
            rules.put(key, rule);
        }
    }

    public Map<String, PolicyRule> getAllRules() {
        return Collections.unmodifiableMap(rules);
    }

    public PolicyRule getRule(String key) {
        return rules.get(key);
    }

    public PolicyRule refund() {
        return getRule("refund");
    }

    public PolicyRule rebook() {
        return getRule("rebook");
    }

    public PolicyRule meals() {
        return getRule("meals");
    }

    public PolicyRule lounge() {
        return getRule("lounge");
    }

    public PolicyRule hotel() {
        return getRule("hotel");
    }

    public PolicyRule taxi() {
        return getRule("taxi");
    }

    public PolicyRule seats() {
        return getRule("seats");
    }

    public PolicyRule waiver() {
        return getRule("waiver");
    }

    public PolicyRule upgrade() {
        return getRule("upgrade");
    }

    public PolicyRule overnight() {
        return getRule("overnight");
    }

    public PolicyRule cashDelay() {
        return getRule("cash_delay");
    }

    public PolicyRule competitor() {
        return getRule("competitor");
    }

    public PolicyRule bizLoss() {
        return getRule("biz_loss");
    }

    public PolicyRule extraBag() {
        return getRule("extra_bag");
    }

    public PolicyRule tierBonus() {
        return getRule("tier_bonus");
    }

    public PolicyRule bankTransfer() {
        return getRule("bank_transfer");
    }

    public PolicyRule mutualExclusivity() {
        return getRule("mutual_exclusivity");
    }

    public PolicyRule returnFlightPolicy() {
        return getRule("return_flight_policy");
    }
}
