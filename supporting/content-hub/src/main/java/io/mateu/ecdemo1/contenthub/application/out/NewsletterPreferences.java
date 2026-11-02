package io.mateu.ecdemo1.contenthub.application.out;

import java.util.Map;
import java.util.Set;

/**
 * What each employee wants from each newsletter: whether to receive it, and which of its sections (none: all
 * of them). Only ever narrows: an employee is never sent what their audience does not let them see. No row:
 * subscribed, to everything — whoever is in a newsletter's audience gets it until they unsubscribe.
 */
public interface NewsletterPreferences {

    record Preference(boolean subscribed, Set<String> sections) {

        public static final Preference DEFAULT = new Preference(true, Set.of());

        public Preference {
            sections = sections == null ? Set.of() : Set.copyOf(sections);
        }

        /** Whether an item of this (newsletter) section goes: every section, or one of the chosen. */
        public boolean wants(String sectionId) {
            return sections.isEmpty() || (sectionId != null && sections.contains(sectionId));
        }

        /** A key for grouping recipients who want the same: "" for the default. */
        public String key() {
            return !subscribed ? "off" : sections.isEmpty() ? "" : String.join(",", new java.util.TreeSet<>(sections));
        }
    }

    Preference of(String userId, String newsletterId);

    /** Every newsletter the employee has said something about. */
    Map<String, Preference> of(String userId);

    void save(String userId, String newsletterId, Preference preference);
}
