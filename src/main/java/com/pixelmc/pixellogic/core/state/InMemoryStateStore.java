package com.pixelmc.pixellogic.core.state;

import com.pixelmc.pixellogic.core.model.StateValueType;
import com.pixelmc.pixellogic.core.model.StateScope;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryStateStore {
    public static final int DEFAULT_MAX_ENTRIES = 1024;

    private final int maxEntries;
    private final Map<StateKey, StateValue> values = new LinkedHashMap<>();

    public InMemoryStateStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryStateStore(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    public synchronized Optional<StateValue> get(StateKey key) {
        return Optional.ofNullable(values.get(key));
    }

    public synchronized void set(StateKey key, StateValue value) {
        requireCapacityFor(key);
        values.put(key, value);
    }

    public synchronized StateValue addInteger(StateKey key, int amount) {
        requireCapacityFor(key);
        StateValue current = values.get(key);
        if (current != null && current.type() != StateValueType.INTEGER) {
            throw new IllegalStateException("State value is not INTEGER: " + key);
        }
        int base = current == null ? 0 : current.asInteger(0);
        int result;
        try {
            result = Math.addExact(base, amount);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "状态「" + key.key() + "」累加失败：结果超出整数允许范围，原值未修改。"
            );
        }
        StateValue next = StateValue.integer(result);
        values.put(key, next);
        return next;
    }

    public synchronized void removeOwner(StateScope scope, String ownerId) {
        values.keySet().removeIf(key -> key.scope() == scope && key.ownerId().equals(ownerId));
    }

    public synchronized void clear() {
        values.clear();
    }

    public synchronized int size() {
        return values.size();
    }

    private void requireCapacityFor(StateKey key) {
        // ponytail: spike memory store fails closed at a fixed cap; replace with durable state lifecycle before broad runtime use.
        if (!values.containsKey(key) && values.size() >= maxEntries) {
            throw new IllegalStateException("PixelLogic 内存状态数量已达上限。");
        }
    }
}
