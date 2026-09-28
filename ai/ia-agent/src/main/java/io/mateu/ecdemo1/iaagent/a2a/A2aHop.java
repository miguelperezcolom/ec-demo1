package io.mateu.ecdemo1.iaagent.a2a;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where a prompt is in a chain of A2A calls, carried from one agent to the next in headers.
 *
 * <p>{@code depth} is how many A2A hops led here: 0 for a prompt a person typed, 1 for an agent
 * called by that agent, and so on. {@code chain} is the agents already on the way, so a cycle —
 * A asks B, B asks A — is refused at the first repeat instead of running until the depth limit.
 * Both are needed: the chain alone does not stop a long line of distinct agents, and the depth alone
 * lets A and B ping-pong until it runs out.
 *
 * <p>{@code noDelegation} is set on the calls that must not fan out further — a guardrail's
 * verdict — so the agent answering gets no peer tools at all for that call.
 *
 * <p>These are headers of this deployment, not of the A2A protocol, which says nothing about call
 * depth. A foreign A2A client that does not send them arrives as depth 1 with an empty chain.
 */
public record A2aHop(int depth, List<String> chain, boolean noDelegation) {

    public static final String DEPTH_HEADER = "X-A2A-Depth";
    public static final String CHAIN_HEADER = "X-A2A-Chain";
    public static final String NO_DELEGATION_HEADER = "X-A2A-No-Delegation";

    public A2aHop {
        chain = chain == null ? List.of() : List.copyOf(chain);
    }

    /** A prompt from a person: nothing before it. */
    public static A2aHop origin() {
        return new A2aHop(0, List.of(), false);
    }

    /** What an incoming A2A request's headers say. Missing or unreadable depth counts as one hop. */
    public static A2aHop fromHeaders(String depth, String chain, String noDelegation) {
        int d;
        try {
            d = depth == null || depth.isBlank() ? 1 : Integer.parseInt(depth.trim());
        } catch (NumberFormatException e) {
            d = 1;
        }
        var agents = chain == null || chain.isBlank() ? List.<String>of()
                : Arrays.stream(chain.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new A2aHop(Math.max(d, 1), agents, "true".equalsIgnoreCase(noDelegation));
    }

    /** The hop a call made by {@code callerAgentId} from here starts: one deeper, caller added. */
    public A2aHop next(String callerAgentId) {
        var agents = new ArrayList<>(chain);
        if (callerAgentId != null && !agents.contains(callerAgentId)) {
            agents.add(callerAgentId);
        }
        return new A2aHop(depth + 1, agents, false);
    }

    /** The same hop, for a call whose answerer must not delegate — a guardrail's. */
    public A2aHop withoutDelegation() {
        return new A2aHop(depth, chain, true);
    }

    public String chainHeader() {
        return String.join(",", chain);
    }
}
