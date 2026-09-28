package io.mateu.ecdemo1.iaagent.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.iaagent.config.AgentConfig;
import io.mateu.ecdemo1.iaagent.config.AgentConfigClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The A2A protocol's shape as this service answers it, without a model behind it. */
class A2aServerTest {

    final ObjectMapper mapper = new ObjectMapper();
    final List<String> ran = new ArrayList<>();
    final List<A2aHop> hops = new ArrayList<>();

    static AgentConfig config(String id) {
        return new AgentConfig(id, "Agent " + id, "Does " + id + " things", "prompt", null,
                List.of(), List.of(), List.of(), List.of());
    }

    final A2aServer server = new A2aServer(
            id -> {
                if (id.equals("off")) {
                    throw new AgentConfigClient.Unavailable("Agent 'off' is disabled");
                }
                return config(id);
            },
            (config, text, contextId, authorization, hop) -> {
                ran.add(config.agentId() + ":" + text + ":" + authorization);
                hops.add(hop);
                return "answer from " + config.agentId();
            },
            2, "http://ia-agent:8095/");

    JsonNode send(String agentId, String body, A2aHop hop) {
        return mapper.valueToTree(server.handle(agentId, body, "Bearer t", hop));
    }

    static String messageSend(String text) {
        return """
                {"jsonrpc":"2.0","id":7,"method":"message/send","params":{"message":{
                  "kind":"message","role":"user","messageId":"m1","contextId":"ctx-1",
                  "parts":[{"kind":"text","text":"%s"}]}}}
                """.formatted(text);
    }

    @Test
    void messageSendAnswersWithAnAgentMessageInTheSameContext() {
        var response = send("b", messageSend("hola"), A2aHop.fromHeaders("1", "a", null));

        assertEquals("2.0", response.path("jsonrpc").asText());
        assertEquals(7, response.path("id").asInt());
        var result = response.path("result");
        assertEquals("message", result.path("kind").asText());
        assertEquals("agent", result.path("role").asText());
        assertEquals("ctx-1", result.path("contextId").asText());
        assertFalse(result.path("messageId").asText().isBlank());
        assertEquals("text", result.path("parts").get(0).path("kind").asText());
        assertEquals("answer from b", result.path("parts").get(0).path("text").asText());
        assertEquals(List.of("b:hola:Bearer t"), ran);
    }

    @Test
    void aCallPastTheDepthLimitIsRefusedWithoutRunningTheAgent() {
        var response = send("b", messageSend("hola"), A2aHop.fromHeaders("3", "a,c", null));
        assertEquals(A2aServer.DEPTH_EXCEEDED, response.path("error").path("code").asInt());
        assertTrue(ran.isEmpty());
    }

    @Test
    void theLimitItselfIsStillAllowed() {
        send("b", messageSend("hola"), A2aHop.fromHeaders("2", "a,c", null));
        assertEquals(1, ran.size());
        assertEquals(2, hops.getFirst().depth());
    }

    @Test
    void aCallBackToAnAgentAlreadyOnTheChainIsACycle() {
        var response = send("a", messageSend("hola"), A2aHop.fromHeaders("2", "a,b", null));
        assertEquals(A2aServer.CYCLE, response.path("error").path("code").asInt());
        assertTrue(ran.isEmpty());
    }

    @Test
    void anUnservableAgentIsAnErrorWithTheCataloguesReason() {
        var response = send("off", messageSend("hola"), A2aHop.fromHeaders("1", "a", null));
        assertEquals(A2aServer.AGENT_UNAVAILABLE, response.path("error").path("code").asInt());
        assertTrue(response.path("error").path("message").asText().contains("disabled"));
    }

    @Test
    void whatIsNotImplementedSaysSoInTheProtocolsOwnCodes() {
        for (var entry : Map.of(
                "message/stream", A2aServer.UNSUPPORTED_OPERATION,
                "tasks/get", A2aServer.TASK_NOT_FOUND,
                "tasks/cancel", A2aServer.TASK_NOT_FOUND,
                "tasks/pushNotificationConfig/set", A2aServer.PUSH_NOTIFICATION_NOT_SUPPORTED,
                "nope", A2aServer.METHOD_NOT_FOUND).entrySet()) {
            var response = send("b", "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"method\":\"" + entry.getKey()
                    + "\",\"params\":{}}", A2aHop.origin());
            assertEquals(entry.getValue(), response.path("error").path("code").asInt(), entry.getKey());
            assertEquals("x", response.path("id").asText());
        }
    }

    @Test
    void malformedRequestsGetTheJsonRpcErrors() {
        assertEquals(A2aServer.PARSE_ERROR,
                send("b", "{not json", A2aHop.origin()).path("error").path("code").asInt());
        assertEquals(A2aServer.INVALID_REQUEST,
                send("b", "{\"id\":1,\"method\":\"message/send\"}", A2aHop.origin()).path("error").path("code").asInt());
        assertEquals(A2aServer.INVALID_PARAMS, send("b",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\",\"params\":{}}", A2aHop.origin())
                .path("error").path("code").asInt());
        assertEquals(A2aServer.CONTENT_TYPE_NOT_SUPPORTED, send("b",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\",\"params\":{\"message\":{\"parts\":"
                        + "[{\"kind\":\"file\",\"file\":{\"uri\":\"x\"}}]}}}", A2aHop.origin())
                .path("error").path("code").asInt());
        assertTrue(ran.isEmpty());
    }

    @Test
    void theAgentCardDescribesTheAgentAtItsOwnUrl() {
        JsonNode card = mapper.valueToTree(server.agentCard("b"));
        assertEquals("0.3.0", card.path("protocolVersion").asText());
        assertEquals("Agent b", card.path("name").asText());
        assertEquals("Does b things", card.path("description").asText());
        assertEquals("http://ia-agent:8095/a2a/b", card.path("url").asText());
        assertEquals("JSONRPC", card.path("preferredTransport").asText());
        assertFalse(card.path("capabilities").path("streaming").asBoolean(true));
        assertEquals("text/plain", card.path("defaultInputModes").get(0).asText());
        assertEquals("b", card.path("skills").get(0).path("id").asText());
        assertEquals("bearer", card.path("securitySchemes").path("bearer").path("scheme").asText());
        assertNull(server.agentCard("off"));
    }

    @Test
    void hopsAreReadFromHeadersAndAForeignCallerCountsAsOneHop() {
        var foreign = A2aHop.fromHeaders(null, null, null);
        assertEquals(1, foreign.depth());
        assertTrue(foreign.chain().isEmpty());
        var next = A2aHop.fromHeaders("1", "a", "true").next("b");
        assertEquals(2, next.depth());
        assertEquals(List.of("a", "b"), next.chain());
        assertFalse(next.noDelegation());
    }
}
