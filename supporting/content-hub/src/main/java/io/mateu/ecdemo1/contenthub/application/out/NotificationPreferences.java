package io.mateu.ecdemo1.contenthub.application.out;

import java.util.Map;

/** How each employee wants to be told of what the intranet publishes (no row: every way there is). */
public interface NotificationPreferences {

    /**
     * @param inApp the platform's bell and the browser's notifications
     * @param email the e-mail that important items also send
     */
    record Preference(boolean inApp, boolean email) {

        public static final Preference DEFAULT = new Preference(true, true);
    }

    Preference of(String userId);

    /** Those who said something: a fan-out asks once, not once per reader. */
    Map<String, Preference> all();

    void save(String userId, Preference preference);
}
