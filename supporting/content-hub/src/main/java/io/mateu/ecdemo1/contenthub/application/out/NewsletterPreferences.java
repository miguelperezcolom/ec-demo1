package io.mateu.ecdemo1.contenthub.application.out;

import java.util.Set;

/**
 * What each employee wants from the newsletter: whether to receive it, and which sections (none: all
 * of them). Only ever narrows: an employee is never sent what their audience does not let them see.
 */
public interface NewsletterPreferences {

    record Preference(boolean subscribed, Set<String> sections) {

        public static final Preference DEFAULT = new Preference(true, Set.of());

        public Preference {
            sections = sections == null ? Set.of() : Set.copyOf(sections);
        }

        /** Whether an item of this section goes: every section, or one of the chosen. */
        public boolean wants(String sectionId) {
            return sections.isEmpty() || (sectionId != null && sections.contains(sectionId));
        }

        /** A key for grouping recipients who want the same: "" for the default. */
        public String key() {
            return !subscribed ? "off" : sections.isEmpty() ? "" : String.join(",", new java.util.TreeSet<>(sections));
        }
    }

    Preference of(String userId);

    void save(String userId, Preference preference);
}
