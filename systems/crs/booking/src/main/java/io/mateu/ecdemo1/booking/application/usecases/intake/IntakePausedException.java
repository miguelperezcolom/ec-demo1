package io.mateu.ecdemo1.booking.application.usecases.intake;

/** A booking made, changed or cancelled while the CRS does not admit them: the demo is being reset. */
public class IntakePausedException extends IllegalStateException {

    public IntakePausedException(String message) {
        super(message);
    }
}
