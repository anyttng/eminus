package com.eminus.session;

final class WorldCleaner {
    static final String THREAD_NAME = "eminus-world-cleaner";
    static final long IDLE_CLOSE_MILLIS = 10_000L;

    private static final long POLL_MILLIS = 1_000L;

    private final Thread thread;

    WorldCleaner(EminusInstance instance) {
        thread = new Thread(() -> run(instance), THREAD_NAME);
        thread.setDaemon(true);
    }

    void start() {
        thread.start();
    }

    void stop() {
        thread.interrupt();

        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void run(EminusInstance instance) {
        while (true) {
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }

            instance.closeIdleRuntimes();
        }
    }
}
