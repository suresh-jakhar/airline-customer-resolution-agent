package com.skyair.resolutionagent.strategy;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.service.PolicyProvider;
import org.springframework.stereotype.Component;

@Component
public class RebookActionExecutor implements ActionExecutor {

    @Override
    public AllowedAction getSupportedAction() {
        return AllowedAction.REBOOK;
    }

    @Override
    public String execute(Customer customer, Booking booking, PolicyProvider policyProvider) {
        String rebookWindow = policyProvider.getPolicies().rebook().timeLimit();
        return "Your rebooking priority request for booking " + booking.pnr() +
               " has been submitted. Our ticketing system will confirm your seat on the next available flight within " + rebookWindow + ".";
    }
}
