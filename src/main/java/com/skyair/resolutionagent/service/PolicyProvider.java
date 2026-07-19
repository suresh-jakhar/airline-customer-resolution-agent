package com.skyair.resolutionagent.service;

import com.skyair.resolutionagent.model.DisruptionPolicies;
import com.skyair.resolutionagent.model.FlightStatus;

import java.util.Map;

public interface PolicyProvider {

    DisruptionPolicies getPolicies();

    String getRawJsonPolicy();

    String generateLlmPolicySection(FlightStatus flightStatus);

    Map<String, Map<String, String>> getActionMetadata();
}
