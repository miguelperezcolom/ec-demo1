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
 */
final class NavigationMarkerFilter {

    private static final String OPEN = "[NAVIGATE:";

    private final StringBuilder held = new StringBuilder();

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
                out.append(held);
                held.setLength(0);
                break;
            }
            out.append(held, 0, bracket);
            held.delete(0, bracket);
            // held starts with '['
            if (held.length() < OPEN.length()) {
                if (OPEN.startsWith(held.toString())) {
                    break; // could still become a marker: wait for more
                }
                out.append('[');
                held.deleteCharAt(0);
                continue;
            }
            if (!held.substring(0, OPEN.length()).equals(OPEN)) {
                out.append('[');
                held.deleteCharAt(0);
                continue;
            }
            int close = held.indexOf("]");
            if (close < 0) {
                break; // a marker, not finished yet
            }
            held.delete(0, close + 1);
        }
        return out.toString();
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
