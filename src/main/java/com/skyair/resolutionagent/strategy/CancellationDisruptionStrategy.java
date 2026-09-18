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
import java.util.List;

@Component
public class CancellationDisruptionStrategy implements DisruptionStrategy {

    @Override
    public boolean supports(FlightStatus status) {
        return status == FlightStatus.CANCELLED;
    }

    @Override
    public Resolution evaluate(Customer customer, Booking booking, Intent intent,
                               List<AllowedAction> executedActions, DisruptionPolicies pol) {
        List<AllowedAction> allowedActions = new ArrayList<>();
        List<String> policies = new ArrayList<>();
        List<Intent> deniedIntents = new ArrayList<>();
        StringBuilder summary = new StringBuilder();

        Flight outbound = booking.outboundFlight();
        summary.append("Flight ").append(outbound.flightNumber()).append(" is CANCELLED due to operational reasons.");

        String rebookWindow = pol.rebook().timeLimit();
        String refundDays = pol.refund().timeLimit();

        policies.add("Cancellation (airline-caused): free rebooking within " + rebookWindow + " OR full refund, customer's choice");

        if (customer.loyaltyTier() == LoyaltyTier.SILVER) {
            policies.add("Loyalty Tier: SILVER member");
        } else if (customer.loyaltyTier() == LoyaltyTier.GOLD || customer.loyaltyTier() == LoyaltyTier.PLATINUM) {
            policies.add("Loyalty Priority: Priority rebooking access for " + customer.loyaltyTier() + " member");
            summary.append(" Priority rebooking access granted for ").append(customer.loyaltyTier()).append(" member.");
        }

        if (booking.returnFlight() != null && booking.returnFlight().status() == FlightStatus.UNAFFECTED) {
            policies.add("Return Flight Confirmed: " + booking.returnFlight().flightNumber() + " is confirmed on schedule; disruption remedies do not apply to return flight");
            summary.append(" Note: Return flight ").append(booking.returnFlight().flightNumber()).append(" is confirmed on schedule.");
        }

        boolean refundAlreadyDone = executedActions.contains(AllowedAction.FULL_REFUND);
        boolean rebookAlreadyDone = executedActions.contains(AllowedAction.REBOOK);

        PolicyRule mutualExcl = pol.mutualExclusivity();

        if (refundAlreadyDone) {
            policies.add(mutualExcl != null ? mutualExcl.summary() : "Mutual Exclusivity: Full refund already initiated; rebooking option closed per airline policy");
            summary.append(" Full refund has already been initiated for booking ").append(booking.pnr()).append(". Rebooking is no longer available.");
            if (intent == Intent.REBOOKING_REQUEST) {
                deniedIntents.add(Intent.REBOOKING_REQUEST);
            }
        } else if (rebookAlreadyDone) {
            policies.add(mutualExcl != null ? mutualExcl.summary() : "Mutual Exclusivity: Rebooking priority already submitted; refund option closed per airline policy");
            summary.append(" Rebooking priority has already been submitted for booking ").append(booking.pnr()).append(". Full refund is no longer available.");
            if (intent == Intent.REFUND_REQUEST) {
                deniedIntents.add(Intent.REFUND_REQUEST);
            }
        } else {
            allowedActions.add(AllowedAction.FULL_REFUND);
            allowedActions.add(AllowedAction.REBOOK);
            summary.append(" Eligible for free rebooking or full refund (processed to original payment method within ").append(refundDays).append(").");
            if (intent == Intent.REFUND_REQUEST) {
                policies.add("Refund Policy: Full refund within " + refundDays + " to original payment method ONLY");
                summary.append(" Full refund option requested.");
            }
        }

        if (intent == Intent.HOTEL_REQUEST) {
            deniedIntents.add(Intent.HOTEL_REQUEST);
            policies.add(pol.hotel() != null ? pol.hotel().denialReason() : "Hotel accommodation is not provided for cancelled flights");
            summary.append(" Hotel accommodation is not covered under cancellation policy.");
        } else if (intent == Intent.COMPENSATION_REQUEST) {
            deniedIntents.add(Intent.COMPENSATION_REQUEST);
            policies.add(pol.bizLoss() != null ? pol.bizLoss().denialReason() : "Cash compensation for business loss is not covered under airline disruption charter");
            summary.append(" Cash compensation for business loss is not covered under airline disruption charter.");
        } else if (intent == Intent.MEAL_VOUCHER_REQUEST) {
            deniedIntents.add(Intent.MEAL_VOUCHER_REQUEST);
            policies.add(pol.meals() != null ? pol.meals().denialReason() : "Meal vouchers apply to delayed flights; cancelled flight remedies are full refund or rebooking");
            summary.append(" Meal vouchers are not applicable to flight cancellations.");
        }

        return new Resolution(allowedActions, deniedIntents, false, null, policies, summary.toString());
    }
}
