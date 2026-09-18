package com.skyair.resolutionagent.strategy;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.model.DisruptionPolicies;
import com.skyair.resolutionagent.model.Flight;
import com.skyair.resolutionagent.model.FlightStatus;
import com.skyair.resolutionagent.model.Intent;
import com.skyair.resolutionagent.model.LoyaltyTier;
import com.skyair.resolutionagent.model.PolicyRule;
import com.skyair.resolutionagent.model.Resolution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class DelayDisruptionStrategy implements DisruptionStrategy {

    @Override
    public boolean supports(FlightStatus status) {
        return status == FlightStatus.DELAYED;
    }

    @Override
    public Resolution evaluate(Customer customer, Booking booking, Intent intent,
                               List<AllowedAction> executedActions, DisruptionPolicies pol) {
        Flight flight = booking.outboundFlight();
        int delay = flight.delayMinutes();
        List<AllowedAction> allowedActions = new ArrayList<>(getDelayAllowedActions(delay, pol));
        List<String> policies = new ArrayList<>();
        List<Intent> deniedIntents = new ArrayList<>();
        StringBuilder summary = new StringBuilder();

        summary.append("Flight ").append(flight.flightNumber())
               .append(" is DELAYED by ").append(delay / 60).append(" hours (new departure: ")
               .append(flight.newDeparture()).append(").");

        int mealInr = pol.meals().maxInr();
        int loungeThreshold = pol.lounge().minDelay();
        int hotelThreshold = pol.hotel().minDelay();

        if (delay >= pol.meals().minDelay()) {
            policies.add("Delay care: Rs." + mealInr + " electronic meal voucher");
        }
        if (delay >= loungeThreshold) {
            policies.add("Lounge access: Complimentary airport lounge access for delays of " + (loungeThreshold / 60) + " hours or more");
        }
        if (delay >= hotelThreshold) {
            policies.add("Hotel accommodation: Day-room hotel accommodation during delayed hours only (overnight stays prohibited)");
        }

        if (customer.loyaltyTier() == LoyaltyTier.SILVER) {
            policies.add("Loyalty Member: SILVER tier");
        } else if (customer.loyaltyTier() == LoyaltyTier.GOLD || customer.loyaltyTier() == LoyaltyTier.PLATINUM) {
            policies.add("Loyalty Member: " + customer.loyaltyTier() + " tier (eligible for priority rebooking access)");
        }

        if (executedActions != null) {
            allowedActions.removeAll(executedActions);
        }

        if (intent == Intent.HOTEL_REQUEST) {
            if (delay < hotelThreshold) {
                deniedIntents.add(Intent.HOTEL_REQUEST);
                policies.add(pol.hotel().denialReason());
                summary.append(" Hotel request DENIED (delay ").append(delay / 60)
                       .append("h is under the ").append(hotelThreshold / 60).append("h threshold). Meal voucher and lounge access remain available.");
            } else {
                policies.add("Hotel policy: Authorized strictly for delayed hours duration (until 20:00 departure); overnight stays prohibited");
                summary.append(" Hotel accommodation authorized for DELAYED HOURS ONLY, not a full-night stay.");
            }
        } else if (intent == Intent.REFUND_REQUEST) {
            deniedIntents.add(Intent.REFUND_REQUEST);
            policies.add(pol.refund().denialReason());
            summary.append(" Monetary refund not applicable for operational delays. Standard delay care amenities provided.");
        } else if (intent == Intent.LOUNGE_ACCESS_REQUEST && delay < loungeThreshold) {
            deniedIntents.add(Intent.LOUNGE_ACCESS_REQUEST);
            policies.add(pol.lounge().denialReason());
            summary.append(" Lounge access not eligible for delays under ").append(loungeThreshold / 60).append(" hours.");
        } else if (intent == Intent.MEAL_VOUCHER_REQUEST) {
            policies.add("Meal voucher rule: Strictly Rs." + mealInr + " electronic meal voucher for airport dining; cash disbursements are prohibited under airline policy");
            summary.append(" Meal voucher of Rs. ").append(mealInr).append(" available. Cash payouts are strictly prohibited.");
        } else if (intent == Intent.COMPENSATION_REQUEST) {
            PolicyRule cashRule = pol.cashDelay();
            if (cashRule != null && cashRule.allowed()) {
                policies.add("Disruption Compensation: " + cashRule.summary());
                summary.append(" Monetary cash compensation authorized under airline charter.");
            } else {
                deniedIntents.add(Intent.COMPENSATION_REQUEST);
                policies.add(cashRule != null ? cashRule.denialReason() : "Monetary compensation is not applicable for flight delays.");
                summary.append(" Monetary compensation is not applicable for flight delays. Standard care amenities provided.");
            }
        }

        return new Resolution(allowedActions, deniedIntents, false, null, policies, summary.toString());
    }

    public static List<AllowedAction> getDelayAllowedActions(int delayMinutes, DisruptionPolicies pol) {
        List<AllowedAction> actions = new ArrayList<>();
        if (pol.meals() != null && pol.meals().allowed() && delayMinutes >= pol.meals().minDelay()) {
            actions.add(AllowedAction.MEAL_VOUCHER);
        }
        if (pol.lounge() != null && pol.lounge().allowed() && delayMinutes >= pol.lounge().minDelay()) {
            actions.add(AllowedAction.LOUNGE_ACCESS);
        }
        if (pol.hotel() != null && pol.hotel().allowed() && delayMinutes >= pol.hotel().minDelay()) {
            actions.add(AllowedAction.HOTEL_DELAYED_HOURS);
        }
        return Collections.unmodifiableList(actions);
    }
}
