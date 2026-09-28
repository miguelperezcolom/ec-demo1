package io.mateu.ecdemo1.iaagent.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Runs a route's guardrail agents over A2A: the input ones on the user's text before the agent
 * sees it, the output ones on the agent's answer before the user does.
 *
 * <p><strong>The contract.</strong> A guardrail is an ordinary agent of the catalogue. It is sent
 * the text, alone, with {@code message/send}, and answers JSON:
 * {@code {"verdict":"ALLOW"|"BLOCK"|"REWRITE","reason":"...","text":"..."}}. BLOCK stops there —
 * the agent is never called for a blocked input, and a blocked answer never reaches the user —
 * and its reason is shown. REWRITE replaces the text, and the next guardrail reads the new one.
 * The answer is read tolerantly, because models wrap JSON: in a code fence, after a sentence, with
 * the verdict in lower case. The first object in the answer is the verdict.
 *
 * <p><strong>When there is no verdict</strong> — the guardrail is unreachable, refuses, or answers
 * something that is not one (a REWRITE with no text counts) — the route's failure mode decides:
 * CLOSED blocks, OPEN lets the text through with a warning in the log.
 *
 * <p>Each call is one A2A hop from the prompt, and asks for no delegation: a guardrail judges the
 * text it is given and does not call other agents to do it.
 */
@Component
public class GuardrailRunner {

    private static final Logger log = LoggerFactory.getLogger(GuardrailRunner.class);

    private final A2aClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public GuardrailRunner(A2aClient client) {
        this.client = client;
    }

    public enum Side { INPUT, OUTPUT }

    /** What is left after the guardrails: the text to go on with, or the reason it was stopped. */
    public record Outcome(String text, boolean blocked, String reason, String blockedBy) {
        static Outcome pass(String text) {
            return new Outcome(text, false, null, null);
        }

        static Outcome block(String reason, String by) {
            return new Outcome(null, true, reason, by);
        }
    }

    /** One guardrail's answer, once read. */
    record Verdict(String verdict, String reason, String text) {
    }

    public Outcome check(Side side, AgentConfig config, String text, String authorization, A2aHop hop) {
        var guardrails = config.guardrailsOrNone();
        List<AgentConfig.Guardrail> list = side == Side.INPUT ? guardrails.input() : guardrails.output();
        var current = text;
        var callHop = hop.next(config.agentId()).withoutDelegation();
        for (var guardrail : list) {
            Verdict verdict;
            try {
                var reply = client.send(guardrail.a2aUrl(), current, authorization, callHop, null);
                verdict = parse(reply.text());
                if (verdict == null) {
                    throw new A2aClient.A2aException("answered something that is not a verdict: "
                            + abbreviate(reply.text()));
                }
            } catch (A2aClient.A2aException e) {
                if (guardrails.failOpen()) {
                    log.warn("{} guardrail {} gave no verdict ({}) — failing OPEN, the text passes",
                            side, guardrail.id(), e.getMessage());
                    continue;
                }
                log.warn("{} guardrail {} gave no verdict ({}) — failing CLOSED, the text is blocked",
                        side, guardrail.id(), e.getMessage());
                return Outcome.block("el control " + name(guardrail) + " no ha podido dar un veredicto ("
                        + e.getMessage() + ")", guardrail.id());
            }
            switch (verdict.verdict()) {
                case "BLOCK" -> {
                    log.info("{} guardrail {} blocked: {}", side, guardrail.id(), verdict.reason());
                    return Outcome.block(verdict.reason() == null || verdict.reason().isBlank()
                            ? "sin motivo indicado" : verdict.reason(), guardrail.id());
                }
                case "REWRITE" -> {
                    log.info("{} guardrail {} rewrote the text: {}", side, guardrail.id(), verdict.reason());
                    current = verdict.text();
                }
                default -> log.debug("{} guardrail {} allowed", side, guardrail.id());
            }
        }
        return Outcome.pass(current);
    }

    /** The sentence the user reads instead of what was blocked. */
    public static String refusal(Side side, Outcome outcome) {
        return side == Side.INPUT
                ? "No puedo atender esta petición: " + outcome.reason()
                : "La respuesta se ha retenido: " + outcome.reason();
    }

    /**
     * The first JSON object in {@code answer}, as a verdict — or null when there is none, when its
     * verdict is not one of the three, or when a REWRITE comes with no text.
     */
    Verdict parse(String answer) {
        if (answer == null) {
            return null;
        }
        int start = answer.indexOf('{');
        while (start >= 0) {
            var node = firstObject(answer, start);
            if (node != null) {
                var verdict = node.path("verdict").asText("").trim().toUpperCase(Locale.ROOT);
                var reason = node.path("reason").asText(null);
                var text = node.path("text").asText(null);
                if (!List.of("ALLOW", "BLOCK", "REWRITE").contains(verdict)) {
                    return null;
                }
                if ("REWRITE".equals(verdict) && (text == null || text.isBlank())) {
                    return null;
                }
                return new Verdict(verdict, reason, text);
            }
            start = answer.indexOf('{', start + 1);
        }
        return null;
    }

    /** The balanced {...} starting at {@code start}, parsed, or null. Braces inside strings count as text. */
    private JsonNode firstObject(String s, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                try {
                    var node = mapper.readTree(s.substring(start, i + 1));
                    return node.isObject() ? node : null;
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String name(AgentConfig.Guardrail g) {
        return g.name() == null || g.name().isBlank() ? g.id() : g.name();
    }

    private static String abbreviate(String s) {
        return s == null ? "" : s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
