package io.mateu.ecdemo1.journey.model;

import java.time.Duration;

/** Durations as a person says them: "340 ms", "4,2 s", "2 min 5 s", "3 h 10 min". */
public final class Durations {

    private Durations() {
    }

    public static String words(Duration duration) {
        if (duration == null) {
            return "—";
        }
        var millis = duration.toMillis();
        if (millis < 1000) {
            return millis + " ms";
        }
        if (millis < 60_000) {
            var tenths = Math.round(millis / 100.0);
            return (tenths / 10) + (tenths % 10 == 0 ? "" : "," + (tenths % 10)) + " s";
        }
        var seconds = millis / 1000;
        if (seconds < 3600) {
            return (seconds / 60) + " min" + (seconds % 60 == 0 ? "" : " " + (seconds % 60) + " s");
        }
        var minutes = seconds / 60;
        if (minutes < 48 * 60) {
            return (minutes / 60) + " h" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + " min");
        }
        return (minutes / (60 * 24)) + " d " + ((minutes / 60) % 24) + " h";
    }
}
