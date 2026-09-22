package io.mateu.ecdemo1.pmsintegration.ohip;

/** Opera did not answer, or answered that it cannot now: retrying is the fix. */
public class PmsTransientException extends RuntimeException {

    public PmsTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
