package com.generalgame;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * A per-turn 30 second deadline. The owning device starts this timer only
 * when its player becomes active. If the player does not roll, onTimeout runs
 * once so that the owning device can generate and transmit its own die result.
 */
public final class LanTurnTimer {
    public interface Listener {
        void onTick(int secondsRemaining);
        void onTimeout();
    }

    public static final int TURN_SECONDS = 30;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private long deadline;
    private int turnId;
    private boolean running;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) {
                running = false;
                listener.onTick(0);
                listener.onTimeout();
                return;
            }
            int seconds = (int) ((remaining + 999L) / 1000L);
            listener.onTick(seconds);
            handler.postDelayed(this, Math.min(250L, remaining));
        }
    };

    public LanTurnTimer(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("listener");
        this.listener = listener;
    }

    public void start(int newTurnId) {
        cancel();
        if (newTurnId < 1) throw new IllegalArgumentException("turnId");
        turnId = newTurnId;
        deadline = SystemClock.elapsedRealtime() + TURN_SECONDS * 1000L;
        running = true;
        listener.onTick(TURN_SECONDS);
        handler.post(ticker);
    }

    public void cancel() {
        running = false;
        handler.removeCallbacks(ticker);
    }

    public boolean isRunning() { return running; }
    public int getTurnId() { return turnId; }

    public int getSecondsRemaining() {
        if (!running) return 0;
        long remaining = deadline - SystemClock.elapsedRealtime();
        return (int) Math.max(0L, (remaining + 999L) / 1000L);
    }
}
