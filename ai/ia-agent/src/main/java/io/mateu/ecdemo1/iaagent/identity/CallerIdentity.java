package io.mateu.ecdemo1.iaagent.identity;

import java.util.List;

/**
 * Who is asking, as the agent can tell from the token the gateway already validated. It is the
 * subject a budget is charged to and a routing rule is matched on, so both features read it and
 * neither has to parse a token itself.
 *
 * <p>{@code anonymous()} is a real value, not an error: a prompt can arrive without a token in a
 * local run, and the agent still answers — it just cannot attribute the spend to anyone or route by
 * a role it does not have.
 */
public record CallerIdentity(String userId, String username, List<String> roles, String tenant) {

    public static CallerIdentity anonymous() {
        return new CallerIdentity(null, null, List.of(), null);
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
