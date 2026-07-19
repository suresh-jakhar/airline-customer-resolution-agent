package com.skyair.resolutionagent.strategy;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.service.PolicyProvider;

public interface ActionExecutor {

    AllowedAction getSupportedAction();

    String execute(Customer customer, Booking booking, PolicyProvider policyProvider);
}
