package io.mateu.ecdemo1.iaagent;

/**
 * Takes the {@code [NAVIGATE:{…}]} markers out of an answer that arrives in pieces, so none of one
 * ever flashes on screen while the answer is being streamed.
 *
 * <p>A marker can be split across any number of chunks — {@code "[NAV"}, {@code "IGATE:{\"rou"},
 * {@code "te\":\"/x\"}]"} — so text from a {@code [} on is held back until it is either clearly not
 * a marker (and released as it was) or a whole one (and dropped). The navigation itself is not
 * handled here: the whole answer is parsed once it has finished, exactly as before streaming, and
 * that is where the navigation events come from.
 *
 * <p>The grammar is the controller's {@code \[NAVIGATE:(\{[^]]*})]}: a marker ends at its first
 * {@code ]}. Not thread-safe; one per answer.
 *
 * <p>What a marker leaves behind follows {@link NavigationMarkers} as far as it can be known before
 * the answer ends: a marker on a line of its own is a command and leaves nothing; a marker written
 * inline after some text, once another marker has already been seen, is a link the model meant and
 * is shown as one ({@code [4MBZS7](/booking/bookings/4MBZS7)}) — so {@code "Nora Duarte: "} is not
 * left pointing at nothing. The first inline marker may still turn out to be the answer's only
 * marker (which navigates), so it is held back as nothing; the final text, which replaces the
 * streamed one, settles it.
 */
final class NavigationMarkerFilter {

    private static final String OPEN = "[NAVIGATE:";

    private final StringBuilder held = new StringBuilder();
    /** Whether the line being shown already has something other than whitespace on it. */
    private boolean lineHasText;
    private int markersSeen;

    /** What of the text seen so far, this chunk included, is safe to show now. Never null. */
    String feed(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        held.append(chunk);
        var out = new StringBuilder();
        while (!held.isEmpty()) {
            int bracket = held.indexOf("[");
            if (bracket < 0) {
                emit(out, held);
                held.setLength(0);
                break;
            }
            emit(out, held.subSequence(0, bracket));
            held.delete(0, bracket);
            // held starts with '['
            if (held.length() < OPEN.length()) {
                if (OPEN.startsWith(held.toString())) {
                    break; // could still become a marker: wait for more
                }
                emit(out, "[");
                held.deleteCharAt(0);
                continue;
            }
            if (!held.substring(0, OPEN.length()).equals(OPEN)) {
                emit(out, "[");
                held.deleteCharAt(0);
                continue;
            }
            int close = held.indexOf("]");
            if (close < 0) {
                break; // a marker, not finished yet
            }
            String json = held.substring(OPEN.length(), close);
            held.delete(0, close + 1);
            markersSeen++;
            if (lineHasText && markersSeen > 1) {
                var link = NavigationMarkers.link(json);
                if (link != null) {
                    emit(out, link);
                }
            }
        }
        return out.toString();
    }

    private void emit(StringBuilder out, CharSequence text) {
        out.append(text);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lineHasText = false;
            } else if (!Character.isWhitespace(c)) {
                lineHasText = true;
            }
        }
    }

    /**
     * What is left once the answer has ended: a {@code [} that never became a marker is text and is
     * returned; a marker that never closed is dropped — the final answer replaces the streamed text
     * anyway, and an unclosed marker was never going to be shown.
     */
    String finish() {
        var rest = held.toString();
        held.setLength(0);
        return rest.startsWith(OPEN) ? "" : rest;
    }
}
