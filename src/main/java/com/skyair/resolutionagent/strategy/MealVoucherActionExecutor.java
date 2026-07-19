package com.skyair.resolutionagent.strategy;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.service.PolicyProvider;
import org.springframework.stereotype.Component;

@Component
public class MealVoucherActionExecutor implements ActionExecutor {

    @Override
    public AllowedAction getSupportedAction() {
        return AllowedAction.MEAL_VOUCHER;
    }

    @Override
    public String execute(Customer customer, Booking booking, PolicyProvider policyProvider) {
        int mealInr = policyProvider.getPolicies().meals().maxAmountInr();
        return "A Rs. " + mealInr + " meal voucher has been issued for booking " + booking.pnr() +
               ". You can show this confirmation or your boarding pass at participating airport restaurants.";
    }
}
