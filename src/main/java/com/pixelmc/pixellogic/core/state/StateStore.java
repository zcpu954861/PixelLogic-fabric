package com.pixelmc.pixellogic.core.state;

import java.util.Optional;

public interface StateStore {
    Optional<StateValue> get(StateKey key);

    void set(StateKey key, StateValue value);

    StateValue addInteger(StateKey key, int amount);

    void remove(StateKey key);
}
