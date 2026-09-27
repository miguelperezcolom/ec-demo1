package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;

/**
 * The state of a catalogue entry as the badge its listing shows. One place, so every listing of
 * the control plane colours the same word the same way: green what an agent can use, grey what is
 * off on purpose, amber what is on and still gives an agent nothing, red what needs someone.
 */
public final class StatusBadge {

    private StatusBadge() {
    }

    public static Status of(String state) {
        var type = switch (state) {
            case "enabled", "usable" -> StatusType.SUCCESS;
            case "disabled" -> StatusType.NONE;
            case "no tools yet", "provider not supported" -> StatusType.WARNING;
            case "no credential" -> StatusType.DANGER;
            default -> StatusType.INFO;
        };
        return new Status(type, Character.toUpperCase(state.charAt(0)) + state.substring(1));
    }

    public static Status enabled(boolean enabled) {
        return of(enabled ? "enabled" : "disabled");
    }
}
