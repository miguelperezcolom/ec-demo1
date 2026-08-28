package io.mateu.ecdemo1.iacp.application.out.probe;

/**
 * Answers "is this catalogue entry describing something that is actually there?".
 *
 * <p>The catalogues are declarative — an entry is written by a person and nothing verifies it on
 * the way in. That is the right trade (a store can be catalogued before it exists, and an outage
 * must not make an entry unsaveable), and it leaves exactly one gap: a typo in a URL is
 * indistinguishable from a server that is down, and both are invisible until an agent's prompt
 * quietly comes back with fewer tools than it should have. This is the button that closes it.
 */
public interface ConnectionProbe<T> {

    Result probe(T target);

    /**
     * @param reachable whether the target answered at all
     * @param detail    what to show the operator — the tool count on success, the failure on the
     *                  way it failed. Never an exception's class name on its own.
     */
    record Result(boolean reachable, String detail) {
        public static Result ok(String detail) { return new Result(true, detail); }
        public static Result failed(String detail) { return new Result(false, detail); }
    }
}
