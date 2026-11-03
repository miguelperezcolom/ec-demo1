package io.mateu.ecdemo1.users.infra.in.ui;

import io.mateu.uidl.UserFacingException;

import java.util.function.Supplier;

/** A use case's refusal, in its own words on the screen: Mateu hides any other exception behind a generic error. */
public final class Refusals {

    private Refusals() {
    }

    public static void shown(Runnable action) {
        answered(() -> {
            action.run();
            return null;
        });
    }

    public static <T> T answered(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new UserFacingException(e.getMessage());
        }
    }
}
