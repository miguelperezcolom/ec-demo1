package io.mateu.ecdemo1.mapping.store;

public enum WaiterStatus {
    /** Waiting for at least one open cause. */
    WAITING,
    /** Every cause resolved; the resume message sent, and sent again until the process answers. */
    RELEASED,
    /** The process resumed and started its successor. Done. */
    RELAUNCHED,
    /** Discarded by a person — the only way out that is not resolving the causes (F012). */
    DISCARDED
}
