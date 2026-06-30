package com.pixelmc.pixellogic.selfcheck;

public final class SelfCheckSupport {
    private SelfCheckSupport() {
    }

    public static void run(String name, CheckedSelfCheck check) {
        try {
            check.run();
            System.out.println(name + " passed");
        } catch (Throwable throwable) {
            throw fail(name + " failed", throwable);
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public static IllegalStateException fail(String message, Throwable cause) {
        return new IllegalStateException(message, cause);
    }

    @FunctionalInterface
    public interface CheckedSelfCheck {
        void run() throws Exception;
    }
}
