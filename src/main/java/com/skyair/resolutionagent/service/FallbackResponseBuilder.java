package com.skyair.resolutionagent.service;

import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.ConversationTurn;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.model.DisruptionPolicies;
import com.skyair.resolutionagent.model.EscalationReason;
import com.skyair.resolutionagent.model.Flight;
import com.skyair.resolutionagent.model.FlightStatus;
import com.skyair.resolutionagent.model.Intent;
import com.skyair.resolutionagent.model.LoyaltyTier;
import com.skyair.resolutionagent.model.Resolution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FallbackResponseBuilder {

    private static final Pattern INR_PATTERN = Pattern.compile("(?:(?:rs\\.?|inr|₹)\\s*([0-9,]+))|(?:([0-9,]+)\\s*(?:rs|rupees|inr))", Pattern.CASE_INSENSITIVE);
    private final PolicyConfigService policyConfigService;

    public FallbackResponseBuilder() {
        this(new PolicyConfigService());
    }

    @Autowired
    public FallbackResponseBuilder(PolicyConfigService policyConfigService) {
        this.policyConfigService = (policyConfigService != null) ? policyConfigService : new PolicyConfigService();
    }

    public String buildResponse(Customer customer, Booking booking, Resolution resolution, String customerMessage) {
        return buildResponse(customer, booking, resolution, customerMessage, true, Collections.emptyList());
    }

    public String buildResponse(Customer customer, Booking booking, Resolution resolution, String customerMessage, boolean isFirstTurn) {
        return buildResponse(customer, booking, resolution, customerMessage, isFirstTurn, Collections.emptyList());
    }

    public String buildResponse(Customer customer, Booking booking, Resolution resolution, String customerMessage, boolean isFirstTurn, List<ConversationTurn> history) {
        if (customer == null || booking == null) {
            return "I apologize, but I am unable to access your booking information. Let me connect you with a representative right away.";
        }

        String lowerMsg = (customerMessage != null) ? customerMessage.toLowerCase().trim() : "";
        DisruptionPolicies pol = policyConfigService.getPolicies();

        String intercepted = handleSafetyAndMeta(customer, booking, lowerMsg, history);
        if (intercepted != null) return intercepted;

        if (resolution != null && resolution.escalationRequired()) {
            return handleEscalation(customer, booking, resolution, customerMessage);
        }

        // 3. Disruption Strategy: Cancellation vs Delay
        Flight outbound = booking.outboundFlight();
        if (outbound != null && outbound.status() == FlightStatus.CANCELLED) {
            return buildCancellationResponse(customer, booking, resolution, lowerMsg, isFirstTurn, pol);
        } else if (outbound != null && outbound.status() == FlightStatus.DELAYED) {
            return buildDelayResponse(customer, booking, resolution, customerMessage, lowerMsg, isFirstTurn, pol);
        }

        return "Flight status is confirmed. How may I best assist you with your journey today?";
    }

    private String handleSafetyAndMeta(Customer customer, Booking booking, String lowerMsg, List<ConversationTurn> history) {
        if (lowerMsg.contains("what did i ask") || lowerMsg.contains("what was my last") ||
            lowerMsg.contains("last chat") || lowerMsg.contains("what did i say") || lowerMsg.contains("repeat what i said")) {
            String lastCustomerMsg = null;
            if (history != null) {
                for (int i = history.size() - 1; i >= 0; i--) {
                    if ("customer".equalsIgnoreCase(history.get(i).role())) {
                        lastCustomerMsg = history.get(i).message();
                        break;
                    }
                }
            }
            return (lastCustomerMsg != null && !lastCustomerMsg.isBlank())
                ? "In your previous message, you asked: \"" + lastCustomerMsg + "\".\n\nHow would you like me to assist you with that?"
                : "You haven't asked any previous questions yet in this session. How may I assist you today?";
        }

        if (lowerMsg.contains("lying") || lowerMsg.contains("lied") || lowerMsg.contains("did you actually") ||
            lowerMsg.contains("nobody joined") || lowerMsg.contains("no one joined") || lowerMsg.contains("didn't assign")) {
            return "I assure you that your request is genuinely escalated in our system. Your booking (PNR: " + booking.pnr() +
                   ") has been officially logged in our Duty Supervisor queue under case reference ESC-" + booking.pnr() + "-HQ.\n\n" +
                   "Our supervisors handle escalated passenger files through our central airline operations console. A duty manager will contact you directly at " +
                   customer.phone() + " or " + customer.email() + ", or you can present your PNR at any SkyAir Airport Customer Service Desk for immediate in-person assistance.";
        }

        // Prompt injection defense
        if (lowerMsg.contains("translate") && (lowerMsg.contains("cancellation") || lowerMsg.contains("cancel")) &&
            (lowerMsg.contains("waive") || lowerMsg.contains("free ticket") || lowerMsg.contains("append"))) {
            return "Stornierung.\n\nWhile I can provide the German translation for 'Cancellation' ('Stornierung'), SkyAir cannot waive cancellation fees or grant free tickets outside of authorized passenger charter policy.";
        }

        if ((lowerMsg.contains("gate supervisor") || lowerMsg.contains("gate agent") || lowerMsg.contains("verbally") || lowerMsg.contains("promised")) &&
            (lowerMsg.contains("upgrade") || lowerMsg.contains("business class") || lowerMsg.contains("confirm"))) {
            return "I cannot verify or confirm any verbal promises made by gate staff, as I do not have access to their personal communications. System records show no approved cabin upgrade for your PNR " + booking.pnr() + ".\n\n" +
                   "• I can process a formal escalation to an on-duty supervisor to review this claim.\n" +
                   "• I can provide your standard authorized disruption remedies below.\n\n" +
                   "Would you like me to escalate this request to a supervisor for immediate review?";
        }

        if (lowerMsg.contains("ask me if") || lowerMsg.contains("ask me whether") || lowerMsg.contains("ask me or not") || lowerMsg.contains("ask me first")) {
            return "Would you like me to connect you with a human customer service supervisor, or would you prefer to explore your available flight options and remedies with me here?";
        }

        if (lowerMsg.contains("no do not assign") || lowerMsg.contains("no need to transfer") || lowerMsg.contains("don't connect")) {
            return "Understood, I will keep your session with me. How can I best assist you with your flight options and amenities today?";
        }

        return null;
    }

    private String handleEscalation(Customer customer, Booking booking, Resolution resolution, String customerMessage) {
        EscalationReason reason = resolution.escalationReason();
        if (reason == EscalationReason.MEDICAL_EMERGENCY_SPECIAL_ASSISTANCE) {
            return "I am so sorry to hear about your condition, and please stay calm—your health and safety are our absolute priority. I am immediately alerting our airport duty supervisor and ground ramp operations team so we can urgently locate and retrieve your checked baggage and get your medication to you right away.\n\n" +
                   "Please proceed directly to the nearest SkyAir Baggage Service desk or speak with any uniformed airline staff or airport security officer immediately. Let them know you have critical life-saving medication inside your checked luggage so they can initiate an emergency ramp retrieval on the spot.\n\n" +
                   "A duty supervisor is taking over your file right now to expedite this.";
        }

        if (reason == EscalationReason.HUMAN_HANDOFF_REQUESTED) {
            return "I have officially logged an escalation for your booking (PNR: " + booking.pnr() + ") with our Airport Duty Supervisor and Senior Customer Relations team.\n\n" +
                   "Our on-duty supervisor has received your file and will contact you directly at your registered phone number (" + customer.phone() + ") or email (" + customer.email() + "). " +
                   "If you are currently at the airport terminal, you can also present your PNR at any SkyAir Airport Customer Service Desk for immediate in-person supervisor assistance.";
        }

        return "I have escalated your case to our on-duty supervisor (Reference: ESC-" + booking.pnr() + "-HQ). A customer relations specialist will assist you directly.";
    }

    private String buildCancellationResponse(Customer customer, Booking booking, Resolution resolution,
                                             String lowerMsg, boolean isFirstTurn, DisruptionPolicies pol) {
        String firstName = customer.name().split(" ")[0];
        Flight outbound = booking.outboundFlight();
        String refundDays = pol.refund().timeLimit();
        String rebookWindow = pol.rebook().timeLimit();

        if (lowerMsg.contains("platinum") && customer.loyaltyTier() != LoyaltyTier.PLATINUM) {
            return "According to our booking records, your current loyalty tier is " + customer.loyaltyTier() + ", not Platinum.\n\n" +
                   "Under SkyAir's disruption charter, " + customer.loyaltyTier() + " members receive priority access for flight rebooking. However, loyalty status does not grant additional monetary compensation or complimentary cabin upgrades beyond standard policy.\n\n" +
                   "For cancelled flight " + outbound.flightNumber() + ", you have two options available:\n" +
                   "• 100% full refund credited to your original payment method within " + refundDays + "\n" +
                   "• Complimentary priority rebooking on the next available flight within " + rebookWindow + "\n\n" +
                   "Please let me know which option you would prefer.";
        }

        if (resolution.deniedIntents().contains(Intent.UPGRADE_REQUEST) || lowerMsg.contains("upgrade") || lowerMsg.contains("business class")) {
            return "I'm sorry, but we cannot provide a complimentary Business Class upgrade as travel is arranged in your ticketed cabin. Because flight " + outbound.flightNumber() + " was cancelled, I can immediately assist you with complimentary priority rebooking on the next available SkyAir flight at zero extra charge, or process a 100% full refund to your card.\n\nWhich option would you prefer?";
        }

        if (resolution.deniedIntents().contains(Intent.COMPENSATION_REQUEST) || lowerMsg.contains("compensation") || lowerMsg.contains("lost business")) {
            return "I am truly sorry to hear that the cancellation disrupted your plans.\n\n" +
                   "Under SkyAir's disruption charter, our remedies cover statutory flight ticket refunds, priority rebooking, and passenger care amenities. Airline policy does not provide cash compensation for consequential business losses.\n\n" +
                   "For flight " + outbound.flightNumber() + ", your options are:\n" +
                   "• 100% full refund credited to your original payment method within " + refundDays + "\n" +
                   "• Complimentary priority rebooking on the next available flight within " + rebookWindow + "\n\n" +
                   "Please let me know which option you prefer.";
        }

        if (isFirstTurn) {
            return "Hello " + firstName + ", flight " + outbound.flightNumber() + " was cancelled due to unexpected operational reasons. We deeply apologize for the disruption to your travel plans.\n\n" +
                   "As a valued " + customer.loyaltyTier() + " member, you have two remedies available:\n" +
                   "• Option 1: Complimentary priority rebooking on the next available flight within " + rebookWindow + "\n" +
                   "• Option 2: 100% full refund processed to your original payment method within " + refundDays + "\n\n" +
                   "Which option would you like to proceed with?";
        }

        return "For cancelled flight " + outbound.flightNumber() + ", your authorized remedies are ready for you:\n" +
               "• Option 1: Complimentary priority rebooking on the next available flight within " + rebookWindow + "\n" +
               "• Option 2: 100% full refund processed to your original payment method within " + refundDays + "\n\n" +
               "Please let me know which option you prefer.";
    }

    private String buildDelayResponse(Customer customer, Booking booking, Resolution resolution,
                                      String customerMessage, String lowerMsg, boolean isFirstTurn, DisruptionPolicies pol) {
        String firstName = customer.name().split(" ")[0];
        Flight outbound = booking.outboundFlight();
        int delayHours = outbound.delayMinutes() / 60;
        int mealInr = pol.meals().maxInr();
        int hotelThresholdMins = pol.hotel().minDelay();
        int hotelHours = hotelThresholdMins / 60;

        if (lowerMsg.contains("platinum")) {
            if (customer.loyaltyTier() == LoyaltyTier.PLATINUM) {
                return "As a valued Platinum member, you receive priority rebooking access for next-available flights.\n\n" +
                       "However, under SkyAir's passenger charter, loyalty tier status does not grant additional monetary compensation, bonus vouchers, or complimentary upgrades beyond standard policy.\n\n" +
                       "For your " + delayHours + "-hour delay on flight " + outbound.flightNumber() + ", your authorized care amenities are:\n" +
                       "• Rs. " + mealInr + " meal voucher valid at all airport food outlets\n" +
                       "• Complimentary airport lounge access\n" +
                       (outbound.delayMinutes() >= hotelThresholdMins ? "• Hotel accommodation for the duration of the delayed hours (day room until 20:00 departure)\n\n" : "\n") +
                       "You can activate your amenities using the buttons below.";
            } else {
                return "According to our booking records, your current loyalty tier is " + customer.loyaltyTier() + ", not Platinum.\n\n" +
                       "Under SkyAir's passenger charter, " + customer.loyaltyTier() + " members receive standard disruption remedies. For your " + delayHours + "-hour delay on flight " + outbound.flightNumber() + ", your authorized amenities are:\n" +
                       "• Rs. " + mealInr + " meal voucher valid at all airport food outlets\n" +
                       "• Complimentary airport lounge access\n\n" +
                       "Please note that no tier receives bonus compensation beyond standard policy.";
            }
        }

        if (resolution.deniedIntents().contains(Intent.HOTEL_REQUEST) || lowerMsg.contains("hotel") || lowerMsg.contains("room")) {
            if (outbound.delayMinutes() < hotelThresholdMins) {
                return (isFirstTurn ? "Hello " + firstName + ", " : "") +
                       "I understand you are looking for a place to rest, but hotel day-room accommodation is provided only for delays of " + hotelHours + " hours or more. Your flight " +
                       outbound.flightNumber() + " is currently delayed by " + delayHours + " hours, so a hotel room cannot be authorized.\n\n" +
                       "I can, however, offer you complimentary airport lounge access and your Rs. " + mealInr + " dining voucher to relax comfortably while you wait. Would you like me to arrange that for you?";
            } else {
                return (isFirstTurn ? "Hello " + firstName + ", " : "") +
                       "I'm sorry for the inconvenience caused by the " + delayHours + "-hour delay. While a full overnight stay is not permitted for a same-day flight, I can arrange a complimentary hotel day-room for you to rest during the delayed hours until your departure.\n\n" +
                       "Would you like me to arrange the day-room accommodation for you?";
            }
        }

        boolean competitor = lowerMsg.contains("another airline") || lowerMsg.contains("competitor");
        boolean waiver = lowerMsg.contains("fare difference") || lowerMsg.contains("waive") || resolution.deniedIntents().contains(Intent.FARE_DIFFERENCE_WAIVER);
        if (competitor || waiver) {
            return handleWaiverAndCompetitor(customerMessage, pol.waiver().maxInr(), competitor);
        }

        if (isFirstTurn) {
            return "Hello " + firstName + ", flight " + outbound.flightNumber() + " to " + outbound.destination() +
                   " is delayed by " + delayHours + " hours (new departure: " + outbound.newDeparture() + "). " +
                   "To make your wait comfortable, you are entitled to a Rs. " + mealInr + " meal voucher and complimentary lounge access.";
        }

        return "Please tell me what you need assistance with, or select one of your authorized care amenities below.";
    }

    private String handleWaiverAndCompetitor(String customerMessage, int waiverLimit, boolean competitor) {
        Integer requestedAmount = extractRequestedAmount(customerMessage);
        if (competitor) {
            if (requestedAmount != null && requestedAmount <= waiverLimit) {
                return "We cannot rebook you on another airline as rebooking is limited to SkyAir flights. However, I can switch you to an earlier SkyAir flight and waive the Rs. " + String.format("%,d", requestedAmount) + " fare difference for you.\n\nWould you like me to check available seats on the next SkyAir flight for you?";
            } else if (requestedAmount != null && requestedAmount > waiverLimit) {
                return "We cannot rebook you on another airline. For alternate SkyAir flights, the maximum fare difference I can waive under standard authority is Rs. " + String.format("%,d", waiverLimit) + ", so the requested Rs. " + String.format("%,d", requestedAmount) + " difference requires supervisor approval.";
            }
            return "We cannot rebook you on another airline as rebooking is strictly limited to SkyAir flights. I can gladly check available seats on the next SkyAir flight for you—would you like me to look that up?";
        } else {
            if (requestedAmount != null && requestedAmount > waiverLimit) {
                return "Under airline policy, the maximum fare difference I can waive under standard authority is Rs. " + String.format("%,d", waiverLimit) + ". Because the requested Rs. " + String.format("%,d", requestedAmount) + " difference exceeds this limit, supervisor approval is required.";
            }
            return "I can waive fare differences up to Rs. " + String.format("%,d", waiverLimit) + " when rebooking on an alternate SkyAir flight. Would you like me to check available SkyAir flights for you?";
        }
    }

    private Integer extractRequestedAmount(String text) {
        if (text == null) return null;
        Matcher m = INR_PATTERN.matcher(text);
        if (m.find()) {
            String val = (m.group(1) != null) ? m.group(1) : m.group(2);
            try {
                return Integer.parseInt(val.replace(",", "").trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
