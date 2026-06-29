package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.timer.TimerContinuation;

import java.time.Duration;
import java.util.UUID;

public interface RuntimeServices {
    void sendPlayerMessage(UUID playerId, String message);

    void debug(String message);

    void scheduleTimer(Duration delay, TimerContinuation continuation);
}
