package com.skyair.resolutionagent.data;

import com.skyair.resolutionagent.model.ConversationTurn;
import com.skyair.resolutionagent.model.Resolution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ConversationStore {

    private final ConcurrentHashMap<String, List<ConversationTurn>> store = new ConcurrentHashMap<>();

    public void recordTurn(String sessionId, ConversationTurn turn) {
        if (sessionId == null || turn == null) return;
        store.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(turn);
    }

    public List<ConversationTurn> getHistory(String sessionId) {
        if (sessionId == null) return Collections.emptyList();
        List<ConversationTurn> turns = store.get(sessionId);
        if (turns == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(turns));
    }

    public Optional<Resolution> getLastResolution(String sessionId) {
        List<ConversationTurn> history = getHistory(sessionId);
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationTurn turn = history.get(i);
            if (turn.resolution() != null) {
                return Optional.of(turn.resolution());
            }
        }
        return Optional.empty();
    }

    public void clear(String sessionId) {
        if (sessionId != null) {
            store.remove(sessionId);
        }
    }
}
