package com.pixelmc.pixellogic.core.state;

import com.pixelmc.pixellogic.core.model.StateValueType;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryStateStore implements StateStore {
    private final ConcurrentMap<StateKey, StateValue> values = new ConcurrentHashMap<>();

    @Override
    public Optional<StateValue> get(StateKey key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void set(StateKey key, StateValue value) {
        values.put(key, value);
    }

    @Override
    public StateValue addInteger(StateKey key, int amount) {
        return values.compute(key, (ignored, current) -> {
            if (current != null && current.type() != StateValueType.INTEGER) {
                throw new IllegalStateException("State value is not INTEGER: " + key);
            }
            int base = current == null ? 0 : current.asInteger(0);
            return StateValue.integer(base + amount);
        });
    }

    @Override
    public void remove(StateKey key) {
        values.remove(key);
    }
}
