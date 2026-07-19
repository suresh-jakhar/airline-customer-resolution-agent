package com.skyair.resolutionagent.service;

import com.skyair.resolutionagent.model.DisruptionPolicies;
import com.skyair.resolutionagent.model.FlightStatus;
import com.skyair.resolutionagent.model.PolicyRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PolicyConfigService implements PolicyProvider {

    private static final Logger log = LoggerFactory.getLogger(PolicyConfigService.class);

    private volatile DisruptionPolicies policies;
    private volatile String rawJsonString;
    private long lastFileModified = -1;

    private static final java.util.List<java.nio.file.Path> POLICY_PATHS = java.util.List.of(
            java.nio.file.Path.of("src/main/resources/airline-policies.json"),
            java.nio.file.Path.of("target/classes/airline-policies.json"),
            java.nio.file.Path.of("airline-policies.json")
    );

    public PolicyConfigService() {
        this.policies = loadPolicies();
    }

    public PolicyConfigService(DisruptionPolicies policies) {
        this.policies = policies != null ? policies : loadPolicies();
        if (policies != null) {
            this.lastFileModified = Long.MAX_VALUE;
        }
    }

    public DisruptionPolicies getPolicies() {
        reloadIfModified();
        return policies;
    }

    public String getRawJsonPolicy() {
        reloadIfModified();
        if (rawJsonString != null && !rawJsonString.isBlank()) {
            return rawJsonString;
        }
        try {
            return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(getPolicies());
        } catch (Exception e) {
            return "{}";
        }
    }

    private synchronized void reloadIfModified() {
        try {
            for (java.nio.file.Path path : POLICY_PATHS) {
                if (java.nio.file.Files.exists(path)) {
                    long currentModified = java.nio.file.Files.getLastModifiedTime(path).toMillis();
                    if (currentModified > lastFileModified) {
                        try (InputStream is = java.nio.file.Files.newInputStream(path)) {
                            ObjectMapper mapper = new ObjectMapper();
                            this.policies = mapper.readValue(is, DisruptionPolicies.class);
                            this.lastFileModified = currentModified;
                            this.rawJsonString = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                            log.info("Hot-reloaded airline policies from {} (meals: Rs. {}, hotel min delay: {}m)", path.toAbsolutePath(), this.policies.meals().maxInr(), this.policies.hotel().minDelay());
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to hot-reload airline policies: {}", e.getMessage());
        }
    }

    private DisruptionPolicies loadPolicies() {
        try {
            for (java.nio.file.Path path : POLICY_PATHS) {
                if (java.nio.file.Files.exists(path)) {
                    this.lastFileModified = java.nio.file.Files.getLastModifiedTime(path).toMillis();
                    try (InputStream is = java.nio.file.Files.newInputStream(path)) {
                        ObjectMapper mapper = new ObjectMapper();
                        DisruptionPolicies loaded = mapper.readValue(is, DisruptionPolicies.class);
                        this.rawJsonString = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                        log.info("Successfully loaded airline policies from filesystem: {} (meals: Rs. {}, hotel min delay: {}m)", path.toAbsolutePath(), loaded.meals().maxInr(), loaded.hotel().minDelay());
                        return loaded;
                    }
                }
            }
            ClassPathResource resource = new ClassPathResource("airline-policies.json");
            try (InputStream is = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                DisruptionPolicies loaded = mapper.readValue(is, DisruptionPolicies.class);
                byte[] bytes = resource.getInputStream().readAllBytes();
                this.rawJsonString = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                log.info("Successfully loaded airline policies from classpath: airline-policies.json");
                return loaded;
            }
        } catch (Exception e) {
            log.warn("Failed to load airline-policies.json, using built-in defaults: {}", e.getMessage());
            return createDefaultPolicies();
        }
    }

    public static DisruptionPolicies createDefaultPolicies() {
        DisruptionPolicies dp = new DisruptionPolicies();
        dp.setRule("refund", new PolicyRule("refund", true, 0, 0, "7 business days", false, false, "100% full refund credited to original payment method within 7 business days for cancelled flights"));
        dp.setRule("rebook", new PolicyRule("rebook", true, 0, 0, "24 hours", false, false, "Complimentary priority rebooking on next available SkyAir flight within 24 hours"));
        dp.setRule("meals", new PolicyRule("meals", true, 0, 500, "immediate", false, false, "Rs.500 electronic dining voucher for airport venues (no cash disbursements)"));
        dp.setRule("lounge", new PolicyRule("lounge", true, 180, 0, "flight departure", false, false, "Complimentary airport lounge access pass for flight delays 3 hours or more (180m)"));
        dp.setRule("hotel", new PolicyRule("hotel", true, 300, 0, "delayed hours only", false, true, "Day-room hotel accommodation authorized for delayed hours only (5h+ delay, overnight stays prohibited)"));
        dp.setRule("taxi", new PolicyRule("taxi", true, 300, 800, "immediate", false, false, "Airport taxi voucher between terminal and hotel when day-room accommodation is authorized"));
        dp.setRule("seats", new PolicyRule("seats", true, 0, 0, "immediate", false, false, "Complimentary standard seat selection during disruption rebooking"));
        dp.setRule("waiver", new PolicyRule("waiver", true, 0, 2500, "immediate", false, true, "Rebooking fare difference waiver authorized up to Rs.2,500; amounts exceeding Rs.2,500 require supervisor approval"));

        dp.setRule("upgrade", new PolicyRule("upgrade", false, 0, 0, "none", false, true, "Complimentary cabin class upgrades (Business/First) are strictly prohibited; requires supervisor authorization"));
        dp.setRule("overnight", new PolicyRule("overnight", false, 0, 0, "none", false, true, "Overnight hotel stays are strictly prohibited for flights departing on the same calendar day"));
        dp.setRule("cash_delay", new PolicyRule("cash_delay", false, 0, 0, "none", false, true, "Cash compensation or cash disbursements for flight delays are strictly prohibited; statutory care is vouchers only"));
        dp.setRule("competitor", new PolicyRule("competitor", false, 0, 0, "none", false, true, "Rebooking on competitor or rival airlines is strictly prohibited; rebooking is limited to SkyAir flights"));
        dp.setRule("biz_loss", new PolicyRule("biz_loss", false, 0, 0, "none", false, true, "Monetary compensation for missed business meetings or consequential losses is strictly prohibited"));
        dp.setRule("extra_bag", new PolicyRule("extra_bag", false, 0, 0, "none", false, true, "Extra baggage fee waivers are strictly prohibited; standard baggage allowance applies"));
        dp.setRule("tier_bonus", new PolicyRule("tier_bonus", false, 0, 0, "none", false, true, "Bonus cash or extra miles payout for Gold/Platinum loyalty tiers is strictly prohibited"));
        dp.setRule("bank_transfer", new PolicyRule("bank_transfer", false, 0, 0, "none", false, true, "Direct bank transfer or cash refunds are strictly prohibited; refunds must credit original payment method only"));

        return dp;
    }

    public PolicyRule getRule(String key) {
        return getPolicies().getRule(key);
    }

    public String generateLlmPolicySection(FlightStatus flightStatus) {
        DisruptionPolicies pol = getPolicies();
        StringBuilder sb = new StringBuilder();
        sb.append("AIRLINE POLICIES (STRICT OPERATIONAL CHARTER RULES):\n");
        sb.append("You must strictly enforce the following binding policy matrix. Do NOT invent remedies or bend rules:\n\n");

        sb.append("[PERMITTED REMEDIES (allowed: true)]:\n");
        for (var entry : pol.getAllRules().entrySet()) {
            String key = entry.getKey();
            PolicyRule rule = entry.getValue();
            if (rule.allowed()) {
                sb.append("• ").append(key).append(": ").append(rule.summary() != null ? rule.summary() : key);
                if (rule.maxInr() > 0) sb.append(" (max Rs.").append(rule.maxInr()).append(")");
                if (rule.minDelay() > 0) sb.append(" (min delay ").append(rule.minDelay() / 60).append("h)");
                if (rule.timeLimit() != null && !rule.timeLimit().equalsIgnoreCase("none")) sb.append(" (time limit: ").append(rule.timeLimit()).append(")");
                sb.append("\n");
            }
        }
        sb.append("\n");

        sb.append("[STRICTLY PROHIBITED REQUESTS (allowed: false) - POLITELY DECLINE]:\n");
        for (var entry : pol.getAllRules().entrySet()) {
            String key = entry.getKey();
            PolicyRule rule = entry.getValue();
            if (!rule.allowed()) {
                sb.append("• ").append(key).append(": ").append(rule.summary() != null ? rule.summary() : "Strictly prohibited").append(" (allowed: false)\n");
            }
        }
        sb.append("\n");

        sb.append("[FLIGHT-SPECIFIC OPERATIONAL STATUS]:\n");
        if (flightStatus == FlightStatus.CANCELLED) {
            String refundTime = pol.refund() != null ? pol.refund().timeLimit() : "7 business days";
            String rebookTime = pol.rebook() != null ? pol.rebook().timeLimit() : "24 hours";
            sb.append("- Cancellation Rules: Passenger chooses EITHER full refund within ")
              .append(refundTime)
              .append(" to original payment method ONLY (cash: false) OR priority rebooking within ")
              .append(rebookTime)
              .append(". No meal vouchers or cash compensation for cancellations. Return flights remain confirmed on schedule.\n\n");
        } else if (flightStatus == FlightStatus.DELAYED) {
            int loungeHours = pol.lounge() != null ? pol.lounge().minDelay() / 60 : 3;
            int hotelHours = pol.hotel() != null ? pol.hotel().minDelay() / 60 : 5;
            int mealInr = pol.meals() != null ? pol.meals().maxInr() : 500;

            sb.append("- Delay Care Entitlements (DO NOT volunteer or mention unless customer explicitly asks about them or asks for options):\n");
            if (pol.meals() != null && pol.meals().allowed()) {
                sb.append("  * Meal Assistance: Rs.").append(mealInr).append(" electronic dining voucher valid at airport restaurants (no cash payouts).\n");
            }
            if (pol.lounge() != null && pol.lounge().allowed()) {
                sb.append("  * Airport Lounge Access: Included for flights delayed ").append(loungeHours).append(" hours or more.\n");
            }
            if (pol.hotel() != null && pol.hotel().allowed()) {
                sb.append("  * Hotel Day-Room Accommodation: Provided strictly for delays of ").append(hotelHours).append(" hours or more (covers delayed hours until departure only; full overnight stays are prohibited). Delays under ").append(hotelHours).append(" hours are not eligible for hotel.\n\n");
            }
        }

        return sb.toString();
    }

    public Map<String, Map<String, String>> getActionMetadata() {
        DisruptionPolicies pol = getPolicies();
        Map<String, Map<String, String>> metadata = new LinkedHashMap<>();

        metadata.put("FULL_REFUND", Map.of(
            "label", "Initiate Full Refund",
            "description", "Original payment method · " + pol.refund().timeLimit()
        ));

        metadata.put("REBOOK", Map.of(
            "label", "Rebook on Next Flight",
            "description", "Complimentary within " + pol.rebook().timeLimit() + " · Priority tier access"
        ));

        metadata.put("MEAL_VOUCHER", Map.of(
            "label", "Apply ₹" + pol.meals().maxInr() + " Meal Voucher",
            "description", "Valid at all airport dining venues"
        ));

        metadata.put("LOUNGE_ACCESS", Map.of(
            "label", "Grant Airport Lounge Pass",
            "description", "Complimentary lounge admission"
        ));

        metadata.put("HOTEL_DELAYED_HOURS", Map.of(
            "label", "Arrange Hotel (Delayed Hours)",
            "description", "Accommodation during delay period (not full night)"
        ));

        return metadata;
    }
}
