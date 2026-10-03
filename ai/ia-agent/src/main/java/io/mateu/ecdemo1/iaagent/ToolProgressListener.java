package io.mateu.ecdemo1.iaagent;

/**
 * Told when one of the agent's tools starts and ends, so a streaming prompt can show the user what
 * the model is doing while it does it — «Llamando a booking_findBookings… 3 s» rather than a
 * spinner that says nothing for half a minute.
 *
 * <p>Handed in per prompt to the three tool wrappers (MCP, RAG, peer A2A), which are the only places
 * that know a call's real outcome: those tools answer a failure to the model as text instead of
 * throwing, so from the outside a failed call and a successful one look the same.
 *
 * <p>Called on whatever thread runs the tool. An implementation must be thread-safe and must not
 * block or throw — a progress line is never worth failing a tool call for.
 */
public interface ToolProgressListener {

    /** For the callers that show no progress: the /chat endpoint, the A2A endpoint, the tests. */
    ToolProgressListener NONE = new ToolProgressListener() {
        @Override
        public void toolStarted(String name, String server, String kind) {
        }

        @Override
        public void toolEnded(String name, String server, String kind, long millis, String error) {
        }
    };

    /**
     * @param name   the tool's name as the model sees it, e.g. {@code booking_findBookings}
     * @param server where it runs: the MCP server's name, the RAG source's or the peer agent's id
     * @param kind   {@code mcp}, {@code rag} or {@code a2a}
     */
    void toolStarted(String name, String server, String kind);

    /** @param error null when the call worked; otherwise why it did not, in a sentence. */
    void toolEnded(String name, String server, String kind, long millis, String error);

    /** Never lets a listener's own failure reach the tool call it is watching. */
    static void started(ToolProgressListener listener, String name, String server, String kind) {
        try {
            listener.toolStarted(name, server, kind);
        } catch (RuntimeException ignored) {
            // A progress line is not worth a tool call.
        }
    }

    /** As {@link #started}, for the end of the call. */
    static void ended(ToolProgressListener listener, String name, String server, String kind,
                      long startedNanos, String error) {
        try {
            listener.toolEnded(name, server, kind, (System.nanoTime() - startedNanos) / 1_000_000, error);
        } catch (RuntimeException ignored) {
            // As above.
        }
    }
}
