package com.pixelmc.pixellogic.core.timer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class WallClockTimerScheduler implements AutoCloseable {
    public static final int DEFAULT_MAX_PENDING_TIMERS = 128;

    private final int maxPendingTimers;
    private final Object lock = new Object();
    private final Map<Integer, ScheduledFuture<?>> pendingTimers = new HashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "PixelLogic-Timer");
        thread.setDaemon(true);
        return thread;
    });
    private int nextTimerId;
    private int generation;
    private boolean closed;

    public WallClockTimerScheduler() {
        this(DEFAULT_MAX_PENDING_TIMERS);
    }

    public WallClockTimerScheduler(int maxPendingTimers) {
        if (maxPendingTimers < 0) {
            throw new IllegalArgumentException("maxPendingTimers must be non-negative");
        }
        this.maxPendingTimers = maxPendingTimers;
    }

    public void schedule(Duration delay, TimerContinuation continuation, Consumer<TimerContinuation> onDue) {
        synchronized (lock) {
            if (closed) {
                throw new RejectedExecutionException("计时器调度器已停止。");
            }
            if (pendingTimers.size() >= maxPendingTimers) {
                throw new RejectedExecutionException("待执行计时器过多，请稍后再试。");
            }
            int timerId = ++nextTimerId;
            int scheduledGeneration = generation;
            try {
                ScheduledFuture<?> future = executor.schedule(
                        () -> runDue(timerId, scheduledGeneration, continuation, onDue),
                        Math.max(1L, delay.toMillis()),
                        TimeUnit.MILLISECONDS
                );
                pendingTimers.put(timerId, future);
            } catch (RuntimeException exception) {
                throw exception;
            }
        }
    }

    public int pendingTimers() {
        synchronized (lock) {
            return pendingTimers.size();
        }
    }

    public int maxPendingTimers() {
        return maxPendingTimers;
    }

    public void clearPendingTimers(String reason) {
        Map<Integer, ScheduledFuture<?>> pending;
        synchronized (lock) {
            generation += 1;
            pending = Map.copyOf(pendingTimers);
            pendingTimers.clear();
        }
        pending.values().forEach(future -> future.cancel(false));
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
        }
        clearPendingTimers("scheduler closed");
        executor.shutdownNow();
    }

    private void runDue(
            int timerId,
            int scheduledGeneration,
            TimerContinuation continuation,
            Consumer<TimerContinuation> onDue
    ) {
        synchronized (lock) {
            if (closed || generation != scheduledGeneration || !pendingTimers.containsKey(timerId)) {
                return;
            }
            pendingTimers.remove(timerId);
        }
        onDue.accept(continuation);
    }
}
