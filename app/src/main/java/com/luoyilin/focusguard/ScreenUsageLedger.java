package com.luoyilin.focusguard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reconstructs unlocked, interactive intervals from independent system events. */
final class ScreenUsageLedger {
    static final int SCREEN_ON = 1;
    static final int SCREEN_OFF = 2;
    static final int KEYGUARD_HIDDEN = 3;
    static final int KEYGUARD_SHOWN = 4;
    static final int SHUTDOWN = 5;
    static final int STARTUP = 6;

    static final class Event {
        final long at;
        final int type;

        Event(long at, int type) {
            this.at = at;
            this.type = type;
        }
    }

    static final class Result {
        final long unlockedMillis;
        final long uncertainStateMillis;
        final List<Long> sessionStarts;

        Result(long unlockedMillis, long uncertainStateMillis, List<Long> sessionStarts) {
            this.unlockedMillis = unlockedMillis;
            this.uncertainStateMillis = uncertainStateMillis;
            this.sessionStarts = sessionStarts;
        }
    }

    static Result calculate(long start, long end, List<Event> input) {
        if (end <= start) return new Result(0, 0, new ArrayList<>());
        List<Event> events = new ArrayList<>(input);
        events.sort(Comparator.comparingLong(event -> event.at));
        Boolean interactive = null;
        Boolean keyguardShown = null;
        long cursor = start;
        long unlocked = 0;
        long uncertain = 0;
        List<Long> sessions = new ArrayList<>();
        for (Event event : events) {
            if (event.at > end) break;
            if (event.at >= start) {
                long until = Math.max(cursor, event.at);
                long interval = until - cursor;
                if (Boolean.TRUE.equals(interactive) && Boolean.FALSE.equals(keyguardShown)) {
                    unlocked += interval;
                } else if (interactive == null || (interactive && keyguardShown == null)) {
                    uncertain += interval;
                }
                cursor = until;
            }
            boolean wasUnlocked = Boolean.TRUE.equals(interactive) && Boolean.FALSE.equals(keyguardShown);
            switch (event.type) {
                case SCREEN_ON: interactive = true; break;
                case SCREEN_OFF: interactive = false; break;
                case KEYGUARD_HIDDEN: keyguardShown = false; break;
                case KEYGUARD_SHOWN: keyguardShown = true; break;
                case SHUTDOWN: interactive = false; keyguardShown = null; break;
                case STARTUP: interactive = null; keyguardShown = null; break;
                default: break;
            }
            if (event.at >= start && event.at < end && !wasUnlocked
                    && Boolean.TRUE.equals(interactive) && Boolean.FALSE.equals(keyguardShown)) {
                sessions.add(event.at);
            }
        }
        long interval = end - cursor;
        if (Boolean.TRUE.equals(interactive) && Boolean.FALSE.equals(keyguardShown)) {
            unlocked += interval;
        } else if (interactive == null || (interactive && keyguardShown == null)) {
            uncertain += interval;
        }
        return new Result(unlocked, uncertain, sessions);
    }
}
