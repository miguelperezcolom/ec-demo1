package io.mateu.ecdemo1.iaagent.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Both answers the protocol allows to message/send, and the ones that are not answers. */
class A2aClientTest {

    final A2aClient client = new A2aClient(null, 5);

    @Test
    void theRequestIsAMessageSendWithOneTextPart() {
        var request = new ObjectMapper().valueToTree(A2aClient.request("hola", "ctx"));
        assertEquals("2.0", request.path("jsonrpc").asText());
        assertEquals("message/send", request.path("method").asText());
        var message = request.path("params").path("message");
        assertEquals("user", message.path("role").asText());
        assertEquals("message", message.path("kind").asText());
        assertEquals("ctx", message.path("contextId").asText());
        assertEquals("text", message.path("parts").get(0).path("kind").asText());
        assertEquals("hola", message.path("parts").get(0).path("text").asText());
        assertTrue(message.hasNonNull("messageId"));
    }

    @Test
    void readsAMessage() {
        var reply = client.read("""
                {"jsonrpc":"2.0","id":1,"result":{"kind":"message","role":"agent","messageId":"x",
                 "contextId":"c1","parts":[{"kind":"text","text":"uno"},{"kind":"text","text":"dos"}]}}
                """);
        assertEquals("uno\ndos", reply.text());
        assertEquals("c1", reply.contextId());
    }

    @Test
    void readsACompletedTaskFromItsArtifacts() {
        var reply = client.read("""
                {"jsonrpc":"2.0","id":1,"result":{"kind":"task","id":"t","contextId":"c1",
                 "status":{"state":"completed"},
                 "artifacts":[{"artifactId":"a","parts":[{"kind":"text","text":"hecho"}]}]}}
                """);
        assertEquals("hecho", reply.text());
    }

    @Test
    void anErrorAFailedTaskOrATaskLeftRunningIsNotAnAnswer() {
        var e = assertThrows(A2aClient.A2aException.class, () -> client.read("""
                {"jsonrpc":"2.0","id":1,"error":{"code":-32050,"message":"A2A depth limit exceeded"}}
                """));
        assertTrue(e.getMessage().contains("depth limit") && e.getMessage().contains("-32050"));
        assertThrows(A2aClient.A2aException.class, () -> client.read("""
                {"jsonrpc":"2.0","id":1,"result":{"kind":"task","status":{"state":"failed"}}}
                """));
        assertThrows(A2aClient.A2aException.class, () -> client.read("""
                {"jsonrpc":"2.0","id":1,"result":{"kind":"task","status":{"state":"working"}}}
                """));
        assertThrows(A2aClient.A2aException.class, () -> client.read("<html>"));
    }
}
