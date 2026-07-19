package com.skyair.resolutionagent.strategy;

import com.skyair.resolutionagent.model.AllowedAction;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ActionExecutorRegistry {

    private final Map<AllowedAction, ActionExecutor> executors = new EnumMap<>(AllowedAction.class);

    public ActionExecutorRegistry(List<ActionExecutor> executorList) {
        if (executorList != null) {
            for (ActionExecutor executor : executorList) {
                executors.put(executor.getSupportedAction(), executor);
            }
        }
    }

    public Optional<ActionExecutor> getExecutor(AllowedAction action) {
        return Optional.ofNullable(executors.get(action));
    }
}
