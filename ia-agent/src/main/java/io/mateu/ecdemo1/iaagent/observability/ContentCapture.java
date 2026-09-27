package io.mateu.ecdemo1.iaagent.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The one door every piece of conversation content goes through on its way into a span: the
 * user's message and the agent's answer on {@code invoke_agent}, the messages sent to the model
 * and its reply on {@code chat}, a tool's arguments and result on {@code execute_tool}. Nothing
 * else in this service writes content to an observation, and Spring AI's own content switches stay
 * off — they would bypass this class, so they could neither be redacted nor truncated.
 *
 * <p>{@code ia.observability.capture-content} ({@code IA_CAPTURE_CONTENT}) picks the mode:
 * <ul>
 *   <li>{@code none} — the default. No content at all; the spans still carry tokens, durations,
 *       tools and outcomes.</li>
 *   <li>{@code redacted} — the content, with the personal data {@link PiiRedactor} recognises
 *       replaced by markers before it leaves the process.</li>
 *   <li>{@code full} — the content as it is.</li>
 * </ul>
 * Any value is truncated to {@code ia.observability.max-attribute-length} characters, with a note
 * saying so; Tempo's distributor truncates an attribute longer than its own limit silently.
 */
@Component
public class ContentCapture {

    public enum Mode { NONE, REDACTED, FULL }

    private final Mode mode;
    private final int maxLength;

    @Autowired
    public ContentCapture(@Value("${ia.observability.capture-content:none}") String mode,
                          @Value("${ia.observability.max-attribute-length:16384}") int maxLength) {
        this(parse(mode), maxLength);
    }

    public ContentCapture(Mode mode, int maxLength) {
        this.mode = mode;
        this.maxLength = Math.max(64, maxLength);
    }

    /** Lenient on purpose: an unrecognised value falls back to none — the safe side. */
    static Mode parse(String value) {
        if (value == null) {
            return Mode.NONE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "full", "true" -> Mode.FULL;
            case "redacted", "redact" -> Mode.REDACTED;
            default -> Mode.NONE;
        };
    }

    public Mode mode() {
        return mode;
    }

    public boolean enabled() {
        return mode != Mode.NONE;
    }

    /**
     * The text as it may go into a span, or null when this mode records no content (or there is
     * none) — callers skip the attribute on null.
     */
    public String prepare(String text) {
        if (mode == Mode.NONE || text == null) {
            return null;
        }
        String value = mode == Mode.REDACTED ? PiiRedactor.redact(text) : text;
        return truncate(value);
    }

    String truncate(String value) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…[truncated: " + value.length() + " chars, "
                + maxLength + " kept]";
    }
}
