package com.skyair.resolutionagent.strategy;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.service.PolicyProvider;
import org.springframework.stereotype.Component;

@Component
public class LoungeAccessActionExecutor implements ActionExecutor {

    @Override
    public AllowedAction getSupportedAction() {
        return AllowedAction.LOUNGE_ACCESS;
    }

    @Override
    public String execute(Customer customer, Booking booking, PolicyProvider policyProvider) {
        return "Complimentary lounge access has been activated for booking " + booking.pnr() +
               ". Present your boarding pass at the lounge entrance for immediate admission.";
    }
}
