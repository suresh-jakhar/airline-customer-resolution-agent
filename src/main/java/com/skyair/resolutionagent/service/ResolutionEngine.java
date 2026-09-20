package com.skyair.resolutionagent.service;

import com.skyair.resolutionagent.model.AllowedAction;
import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.model.Flight;
import com.skyair.resolutionagent.model.Intent;
import com.skyair.resolutionagent.model.Resolution;

import java.util.List;

public interface ResolutionEngine {

    Resolution evaluate(Customer customer, Booking booking, Intent intent);

    Resolution evaluate(Customer customer, Booking booking, Intent intent, List<AllowedAction> executedActions);

    List<AllowedAction> getAvailableActions(Flight flight);

    List<AllowedAction> getDelayAllowedActions(int delayMinutes);

    PolicyConfigService getPolicyConfigService();
}
