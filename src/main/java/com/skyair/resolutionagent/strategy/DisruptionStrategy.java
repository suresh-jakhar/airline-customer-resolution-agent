package com.skyair.resolutionagent.strategy;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.model.DisruptionPolicies;
import com.skyair.resolutionagent.model.FlightStatus;
import com.skyair.resolutionagent.model.Intent;
import com.skyair.resolutionagent.model.Resolution;

import java.util.List;

public interface DisruptionStrategy {

    boolean supports(FlightStatus status);

    Resolution evaluate(Customer customer, Booking booking, Intent intent,
                        List<AllowedAction> executedActions, DisruptionPolicies policies);
}
