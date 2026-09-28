package io.mateu.ecdemo1.integration.model.process;

/**
 * What a step of the integration's processes reports, read by the definitions' guards. A step
 * that cannot go on does not fail: it answers WAIT, having registered its causes, and the process
 * waits for them.
 */
public enum Outcome {
    /** Ready to go on. */
    OK,
    /** Written to the PMS: created, updated, cancelled. */
    DONE,
    /** Nothing to write: the PMS already holds this version or a newer one. */
    STALE,
    /** Blocked on one or more causes, already registered. */
    WAIT,
    /** Nothing for this process to do. */
    SKIP
}
