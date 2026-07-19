package com.skyair.resolutionagent.service;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.model.DisruptionPolicies;
import com.skyair.resolutionagent.model.EscalationReason;
import com.skyair.resolutionagent.model.Flight;
import com.skyair.resolutionagent.model.FlightStatus;
import com.skyair.resolutionagent.model.Intent;
import com.skyair.resolutionagent.model.PolicyRule;
import com.skyair.resolutionagent.model.Resolution;
import com.skyair.resolutionagent.strategy.CancellationDisruptionStrategy;
import com.skyair.resolutionagent.strategy.DelayDisruptionStrategy;
import com.skyair.resolutionagent.strategy.DisruptionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PolicyEngine implements ResolutionEngine {

    private final PolicyConfigService policyConfigService;
    private final List<DisruptionStrategy> strategies;

    public PolicyEngine() {
        this(new PolicyConfigService(), List.of(new CancellationDisruptionStrategy(), new DelayDisruptionStrategy()));
    }

    public PolicyEngine(PolicyConfigService policyConfigService) {
        this(policyConfigService, List.of(new CancellationDisruptionStrategy(), new DelayDisruptionStrategy()));
    }

    @Autowired
    public PolicyEngine(PolicyConfigService policyConfigService, List<DisruptionStrategy> strategies) {
        this.policyConfigService = (policyConfigService != null) ? policyConfigService : new PolicyConfigService();
        this.strategies = (strategies != null && !strategies.isEmpty())
            ? strategies
            : List.of(new CancellationDisruptionStrategy(), new DelayDisruptionStrategy());
    }

    @Override
    public PolicyConfigService getPolicyConfigService() {
        return policyConfigService;
    }

    @Override
    public Resolution evaluate(Customer customer, Booking booking, Intent intent) {
        return evaluate(customer, booking, intent, Collections.emptyList());
    }

    @Override
    public Resolution evaluate(Customer customer, Booking booking, Intent intent, List<AllowedAction> executedActions) {
        if (customer == null || booking == null) {
            return new Resolution(
                Collections.emptyList(),
                Collections.emptyList(),
                true,
                EscalationReason.AGENT_LACKS_AUTHORITY,
                List.of("Missing verified customer or booking record"),
                "Unable to locate verified customer or booking details. Escalation required."
            );
        }

        Flight outbound = booking.outboundFlight();
        Intent safeIntent = (intent == null) ? Intent.GENERAL_INQUIRY : intent;
        List<AllowedAction> safeExecuted = (executedActions == null) ? Collections.emptyList() : executedActions;
        DisruptionPolicies pol = policyConfigService.getPolicies();

        if (safeIntent == Intent.LEGAL_COMPLAINT) {
            return new Resolution(
                Collections.emptyList(),
                List.of(Intent.LEGAL_COMPLAINT),
                true,
                EscalationReason.LEGAL_THREAT_OR_FORMAL_COMPLAINT,
                List.of("Legal threat or formal complaint: immediate escalation to specialist support"),
                "Customer indicated legal action or formal complaint. Immediate supervisor escalation required."
            );
        }

        if (safeIntent == Intent.HUMAN_AGENT_REQUEST) {
            List<AllowedAction> standardActions = new ArrayList<>(getAvailableActions(outbound));
            standardActions.removeAll(safeExecuted);
            return new Resolution(
                Collections.unmodifiableList(standardActions),
                List.of(Intent.HUMAN_AGENT_REQUEST),
                true,
                EscalationReason.HUMAN_HANDOFF_REQUESTED,
                List.of("Human Agent Request: Customer explicitly requested human manager handoff; escalating to on-duty supervisor"),
                "Customer explicitly requested transfer to a human manager. Automated actions paused, supervisor takeover initiated."
            );
        }

        if (safeIntent == Intent.EMERGENCY_MEDICAL_ASSISTANCE) {
            List<AllowedAction> standardActions = new ArrayList<>(getAvailableActions(outbound));
            standardActions.removeAll(safeExecuted);
            return new Resolution(
                Collections.unmodifiableList(standardActions),
                List.of(Intent.EMERGENCY_MEDICAL_ASSISTANCE),
                true,
                EscalationReason.MEDICAL_EMERGENCY_SPECIAL_ASSISTANCE,
                List.of(
                    "Medical Emergency: Urgent escalation to Airport Duty Supervisor & Special Assistance Team",
                    "Medication Priority: Immediate ground luggage retrieval alert flagged if life-saving medicine is checked"
                ),
                "Customer reported acute medical condition / medication in baggage. Urgent airport medical and special assistance escalation initiated."
            );
        }

        if (safeIntent == Intent.UPGRADE_REQUEST) {
            PolicyRule upgradeRule = pol.upgrade();
            boolean upgradeAllowed = (upgradeRule != null && upgradeRule.allowed());
            List<String> policies = new ArrayList<>();
            if (upgradeAllowed) {
                policies.add("Cabin Upgrades: " + upgradeRule.summary());
            } else {
                policies.add(upgradeRule != null ? upgradeRule.denialReason() : "Free cabin class upgrades are strictly prohibited under airline policy for all passengers");
                if (booking.returnFlight() != null && booking.returnFlight().status() == FlightStatus.UNAFFECTED) {
                    policies.add("Return Flight Confirmed: Return leg " + booking.returnFlight().flightNumber() + " is confirmed on schedule; disruption remedies apply only to disrupted flight");
                }
                policies.add("Authority Limit: Agent cannot approve cabin upgrades beyond stated policy; request denied per airline charter");
            }

            List<AllowedAction> standardActions = new ArrayList<>(getAvailableActions(outbound));
            standardActions.removeAll(safeExecuted);

            String summary = upgradeAllowed
                ? "Customer requested class upgrade. Request APPROVED per airline charter."
                : "Customer requested complimentary class upgrade. Request DENIED per airline charter. Standard disruption remedies remain accessible.";

            List<Intent> deniedList = upgradeAllowed ? Collections.emptyList() : List.of(Intent.UPGRADE_REQUEST);

            return new Resolution(
                Collections.unmodifiableList(standardActions),
                deniedList,
                false,
                null,
                policies,
                summary
            );
        }

        if (safeIntent == Intent.FARE_DIFFERENCE_WAIVER) {
            PolicyRule waiverRule = pol.waiver();
            int waiverLimit = (waiverRule != null) ? waiverRule.maxInr() : 2500;
            List<AllowedAction> standardActions = new ArrayList<>(getAvailableActions(outbound));
            standardActions.removeAll(safeExecuted);

            return new Resolution(
                Collections.unmodifiableList(standardActions),
                List.of(Intent.FARE_DIFFERENCE_WAIVER),
                false,
                null,
                List.of(
                    "Fare difference waiver: authorized up to Rs." + waiverLimit + " on SkyAir flights; amounts exceeding Rs." + waiverLimit + " require supervisor authorization",
                    pol.competitor() != null ? pol.competitor().denialReason() : "Rebooking is strictly limited to SkyAir flights"
                ),
                "Fare difference waiver policy: authorized up to Rs." + waiverLimit + " on SkyAir flights. Rebooking is strictly limited to SkyAir flights."
            );
        }

        for (DisruptionStrategy strategy : strategies) {
            if (strategy.supports(outbound.status())) {
                return strategy.evaluate(customer, booking, safeIntent, safeExecuted, pol);
            }
        }

        return new Resolution(
            Collections.emptyList(),
            Collections.emptyList(),
            false,
            null,
            List.of("Flight is operating normally"),
            "Flight status is confirmed. No active disruption remedies needed."
        );
    }

    @Override
    public List<AllowedAction> getDelayAllowedActions(int delayMinutes) {
        return DelayDisruptionStrategy.getDelayAllowedActions(delayMinutes, policyConfigService.getPolicies());
    }

    @Override
    public List<AllowedAction> getAvailableActions(Flight flight) {
        if (flight == null) return Collections.emptyList();
        if (flight.status() == FlightStatus.CANCELLED) {
            return List.of(AllowedAction.REBOOK, AllowedAction.FULL_REFUND);
        }
        if (flight.status() == FlightStatus.DELAYED) {
            return getDelayAllowedActions(flight.delayMinutes());
        }
        return Collections.emptyList();
    }
}
