package com.skyair.resolutionagent.strategy;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.service.PolicyProvider;
import org.springframework.stereotype.Component;

@Component
public class RefundActionExecutor implements ActionExecutor {

    @Override
    public AllowedAction getSupportedAction() {
        return AllowedAction.FULL_REFUND;
    }

    @Override
    public String execute(Customer customer, Booking booking, PolicyProvider policyProvider) {
        String refundDays = policyProvider.getPolicies().refund().timeLimit();
        return "I have initiated your full refund request for booking " + booking.pnr() +
               ". The refund will be credited to your original payment method within " + refundDays + ".";
    }
}
