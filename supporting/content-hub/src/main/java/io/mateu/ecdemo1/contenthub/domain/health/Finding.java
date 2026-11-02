package io.mateu.ecdemo1.contenthub.domain.health;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One thing a check found, about one subject (an item, a medium, a slot, the homes). Its {@link #key} is
 * what it is — the check, the subject and the target — and never how it is described, so the same problem
 * keeps its key from one run to the next (and a «revisado» stays on it) while its wording changes ("caduca
 * en 5 días", then 4).
 *
 * @param target what is wrong in the subject: the URL, the medium, the referenced item; "" when the subject
 *               itself is the problem
 * @param problem what to tell the editor
 * @param fix     the obvious way to fix it, which the screen offers
 * @param size    bytes, for an orphan medium; 0 otherwise
 */
public record Finding(String key, HealthCheck check, Subject subject, String subjectId, String subjectTitle,
                      String target, String problem, Fix fix, long size) {

    /** What a finding is about, and so where its link goes. */
    public enum Subject {
        ITEM("Contenido"), MEDIA("Medio"), SLOT("Slot"), HOMES("Inicios"), MENU("Menú");

        private final String label;

        Subject(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** The fix the screen offers. Never applied by itself: the editor does it. */
    public enum Fix {
        /** Open the item's form. */
        EDIT_ITEM,
        /** Open the medium in the library. */
        OPEN_MEDIA,
        /** Ask the AI for a text alternative, to review and apply. */
        SUGGEST_ALT,
        /** Delete the medium, with confirmation, content:admin only. */
        DELETE_MEDIA,
        /** Change it in the control console (Intranet → Slots, Inicio or Menús): the subject says which. */
        CONFIGURE
    }

    public Finding {
        target = target == null ? "" : target;
        subjectTitle = subjectTitle == null || subjectTitle.isBlank() ? subjectId : subjectTitle;
    }

    public static Finding of(HealthCheck check, Subject subject, String subjectId, String subjectTitle, String target,
                             String problem, Fix fix) {
        return new Finding(key(check, subject, subjectId, target), check, subject, subjectId, subjectTitle, target,
                problem, fix, 0);
    }

    public Finding withSize(long bytes) {
        return new Finding(key, check, subject, subjectId, subjectTitle, target, problem, fix, bytes);
    }

    /** Stable and short: the first 16 hex digits of SHA-256 of what the finding is. */
    public static String key(HealthCheck check, Subject subject, String subjectId, String target) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest((check.name() + "\u0000" + subject.name() + "\u0000"
                    + subjectId + "\u0000" + (target == null ? "" : target)).getBytes(StandardCharsets.UTF_8));
            return check.name().toLowerCase().replace('_', '-') + "-" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
