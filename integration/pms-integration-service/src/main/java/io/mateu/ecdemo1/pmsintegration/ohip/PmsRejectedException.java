package io.mateu.ecdemo1.pmsintegration.ohip;

/**
 * Opera answered, and the answer is no — an invalid payload, a code the property does not have, no
 * room left. Retrying gives the same no: someone has to look (HLA, «4xx determinista»).
 */
public class PmsRejectedException extends RuntimeException {

    final int status;
    final String errorCode;

    public PmsRejectedException(int status, String errorCode, String detail) {
        super(detail);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    /**
     * Opera's no as a cause keeps it, for the person who resolves it: its words and its error code
     * («There are not enough rooms available on Room Type level — RSV00138»), and the HTTP status when
     * Opera gave no words.
     */
    public String reason() {
        var words = getMessage() == null || getMessage().isBlank() ? "Opera answered " + status : getMessage();
        return errorCode == null || errorCode.isBlank() ? words : words + " — " + errorCode;
    }
}
