package com.pixelmc.pixellogic.core.timer;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class WallClockTimerScheduler implements AutoCloseable {
    public static final int DEFAULT_MAX_PENDING_TIMERS = 128;

    private final int maxPendingTimers;
    private final AtomicInteger pendingTimers = new AtomicInteger();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "PixelLogic-Timer");
        thread.setDaemon(true);
        return thread;
    });

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
        int pending = pendingTimers.incrementAndGet();
        if (pending > maxPendingTimers) {
            pendingTimers.decrementAndGet();
            throw new RejectedExecutionException("待执行计时器过多，请稍后再试。");
        }
        try {
            executor.schedule(() -> {
                try {
                    onDue.accept(continuation);
                } finally {
                    pendingTimers.decrementAndGet();
                }
            }, Math.max(1L, delay.toMillis()), TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            pendingTimers.decrementAndGet();
            throw exception;
        }
    }

    public int pendingTimers() {
        return pendingTimers.get();
    }

    public int maxPendingTimers() {
        return maxPendingTimers;
    }

    @Override
    public void close() {
        executor.shutdownNow();
        pendingTimers.set(0);
    }
}
