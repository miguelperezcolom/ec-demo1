package io.mateu.ecdemo1.journey.model;

/** How a hop went: done, waited (a lock, a cause, a retry), failed, or just something to know. */
public enum Tone {
    OK, WAIT, ERROR, INFO
}
