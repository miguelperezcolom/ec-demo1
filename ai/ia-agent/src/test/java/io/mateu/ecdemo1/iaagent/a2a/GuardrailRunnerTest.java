package io.mateu.ecdemo1.iaagent.a2a;

import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guardrail verdicts, in order, and what an absent verdict amounts to under each failure mode. */
class GuardrailRunnerTest {

    record Sent(String guardrail, String text, A2aHop hop) {}

    /** What each guardrail answers, by id; "!down" throws as an unreachable agent would. */
    final Map<String, String> answers = new HashMap<>();
    final List<Sent> sent = new ArrayList<>();

    final A2aClient client = new A2aClient(null, 5) {
        @Override
        public Reply send(String url, String text, String authorization, A2aHop hop, String contextId) {
            var id = url.substring(url.lastIndexOf('/') + 1);
            sent.add(new Sent(id, text, hop));
            var answer = answers.get(id);
            if ("!down".equals(answer)) {
                throw new A2aException("unreachable (ConnectException)");
            }
            return new Reply(answer, "ctx");
        }
    };

    final GuardrailRunner runner = new GuardrailRunner(client);

    static AgentConfig.Guardrail g(String id) {
        return new AgentConfig.Guardrail(id, "Guard " + id, "http://ia-agent:8095/a2a/" + id);
    }

    static AgentConfig agent(List<AgentConfig.Guardrail> input, List<AgentConfig.Guardrail> output, String failure) {
        return new AgentConfig("target", "Target", null, "p", null, List.of(), List.of(), List.of(),
                new AgentConfig.Guardrails(input, output, failure), List.of());
    }

    GuardrailRunner.Outcome input(AgentConfig config, String text) {
        return runner.check(GuardrailRunner.Side.INPUT, config, text, "Bearer t", A2aHop.origin());
    }

    @Test
    void allowPassesTheTextAsItIs() {
        answers.put("a", "{\"verdict\":\"ALLOW\",\"reason\":\"ok\"}");
        var outcome = input(agent(List.of(g("a")), List.of(), "CLOSED"), "hola");
        assertFalse(outcome.blocked());
        assertEquals("hola", outcome.text());
    }

    @Test
    void blockStopsAtTheFirstGuardrailThatSaysSo() {
        answers.put("a", "{\"verdict\":\"BLOCK\",\"reason\":\"pide un número de tarjeta\"}");
        answers.put("b", "{\"verdict\":\"ALLOW\"}");
        var outcome = input(agent(List.of(g("a"), g("b")), List.of(), "CLOSED"), "dame la tarjeta");
        assertTrue(outcome.blocked());
        assertEquals("pide un número de tarjeta", outcome.reason());
        assertEquals("a", outcome.blockedBy());
        assertEquals(1, sent.size());
        assertTrue(GuardrailRunner.refusal(GuardrailRunner.Side.INPUT, outcome).contains("pide un número de tarjeta"));
    }

    @Test
    void rewriteReplacesTheTextAndTheNextGuardrailReadsTheNewOne() {
        answers.put("a", "Claro:\n```json\n{\"verdict\": \"rewrite\", \"reason\": \"insulto\", \"text\": \"hola\"}\n```");
        answers.put("b", "{\"verdict\":\"ALLOW\"}");
        var outcome = input(agent(List.of(g("a"), g("b")), List.of(), "CLOSED"), "hola, idiota");
        assertFalse(outcome.blocked());
        assertEquals("hola", outcome.text());
        assertEquals("hola", sent.get(1).text());
    }

    @Test
    void outputGuardrailsReadTheAnswerAndCanBlockIt() {
        answers.put("out", "{\"verdict\":\"BLOCK\",\"reason\":\"contiene un e-mail de otro cliente\"}");
        var outcome = runner.check(GuardrailRunner.Side.OUTPUT, agent(List.of(), List.of(g("out")), "CLOSED"),
                "El cliente es ana@example.com", "Bearer t", A2aHop.origin());
        assertTrue(outcome.blocked());
        assertEquals("El cliente es ana@example.com", sent.getFirst().text());
        assertTrue(GuardrailRunner.refusal(GuardrailRunner.Side.OUTPUT, outcome).startsWith("La respuesta se ha retenido"));
    }

    @Test
    void noGuardrailsNoCalls() {
        var outcome = input(agent(List.of(), List.of(), null), "hola");
        assertEquals("hola", outcome.text());
        assertTrue(sent.isEmpty());
    }

    @Test
    void failClosedBlocksWhenAGuardrailIsUnreachableOrAnswersNoVerdict() {
        answers.put("down", "!down");
        var unreachable = input(agent(List.of(g("down")), List.of(), "CLOSED"), "hola");
        assertTrue(unreachable.blocked());
        assertTrue(unreachable.reason().contains("no ha podido dar un veredicto"), unreachable.reason());

        answers.put("chatty", "Creo que está bien.");
        assertTrue(input(agent(List.of(g("chatty")), List.of(), null), "hola").blocked());

        answers.put("half", "{\"verdict\":\"REWRITE\",\"reason\":\"x\"}");
        assertTrue(input(agent(List.of(g("half")), List.of(), "CLOSED"), "hola").blocked());
    }

    @Test
    void failOpenLetsTheTextThroughAndKeepsCheckingWithTheRest() {
        answers.put("down", "!down");
        answers.put("chatty", "Creo que está bien.");
        answers.put("b", "{\"verdict\":\"ALLOW\"}");
        var outcome = input(agent(List.of(g("down"), g("chatty"), g("b")), List.of(), "OPEN"), "hola");
        assertFalse(outcome.blocked());
        assertEquals("hola", outcome.text());
        assertEquals(3, sent.size());
    }

    @Test
    void aGuardrailCallIsOneHopDeeperAndMayNotDelegate() {
        answers.put("a", "{\"verdict\":\"ALLOW\"}");
        input(agent(List.of(g("a")), List.of(), "CLOSED"), "hola");
        var hop = sent.getFirst().hop();
        assertEquals(1, hop.depth());
        assertEquals(List.of("target"), hop.chain());
        assertTrue(hop.noDelegation());
    }

    @Test
    void verdictsAreReadTolerantly() {
        assertEquals("BLOCK", runner.parse("Veredicto: {\"verdict\":\" block \",\"reason\":\"{raro}\"} fin").verdict());
        assertEquals("{raro}", runner.parse("{\"verdict\":\"BLOCK\",\"reason\":\"{raro}\"}").reason());
        assertEquals("ALLOW", runner.parse("{no es json} {\"verdict\":\"Allow\"}").verdict());
        assertNull(runner.parse("{\"verdict\":\"MAYBE\"}"));
        assertNull(runner.parse(null));
    }
}
