package com.skyair.resolutionagent.strategy;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.service.PolicyProvider;
import org.springframework.stereotype.Component;

@Component
public class HotelDelayedHoursActionExecutor implements ActionExecutor {

    @Override
    public AllowedAction getSupportedAction() {
        return AllowedAction.HOTEL_DELAYED_HOURS;
    }

    @Override
    public String execute(Customer customer, Booking booking, PolicyProvider policyProvider) {
        return "Hotel accommodation has been arranged for booking " + booking.pnr() +
               " for the duration of the delay period. Please report to the SkyAir transit service desk.";
    }
}
