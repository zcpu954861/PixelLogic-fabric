package com.pixelmc.pixellogic.core.timer;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class WallClockTimerScheduler implements AutoCloseable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "PixelLogic-Timer");
        thread.setDaemon(true);
        return thread;
    });

    public void schedule(Duration delay, TimerContinuation continuation, Consumer<TimerContinuation> onDue) {
        executor.schedule(() -> onDue.accept(continuation), Math.max(1L, delay.toMillis()), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
