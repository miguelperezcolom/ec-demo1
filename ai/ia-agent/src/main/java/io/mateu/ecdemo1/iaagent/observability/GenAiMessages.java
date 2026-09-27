package io.mateu.ecdemo1.iaagent.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One round trip to the model as OpenTelemetry's GenAI semantic conventions write it:
 * {@code gen_ai.system_instructions}, {@code gen_ai.input.messages} and
 * {@code gen_ai.output.messages}, each a JSON array of messages made of typed parts —
 * {@code text}, {@code tool_call} (id, name, arguments) and {@code tool_call_response} (id,
 * response). The raw JSON, unredacted and untruncated: {@link ContentCapture} does both.
 */
record GenAiMessages(String systemInstructions, String input, String output) {

    private static final ObjectMapper JSON = new ObjectMapper();

    static GenAiMessages of(Prompt prompt, ChatResponse response) {
        var system = new ArrayList<Map<String, Object>>();
        var input = new ArrayList<Map<String, Object>>();
        if (prompt != null) {
            for (Message message : prompt.getInstructions()) {
                if (message.getMessageType() == MessageType.SYSTEM) {
                    system.add(text(message.getText()));
                } else {
                    input.add(message(message));
                }
            }
        }
        var output = new ArrayList<Map<String, Object>>();
        if (response != null) {
            for (Generation generation : response.getResults()) {
                var m = message(generation.getOutput());
                var finish = generation.getMetadata() != null ? generation.getMetadata().getFinishReason() : null;
                if (finish != null) {
                    m.put("finish_reason", finish.toLowerCase());
                }
                output.add(m);
            }
        }
        return new GenAiMessages(system.isEmpty() ? null : json(system), json(input),
                response == null ? null : json(output));
    }

    private static Map<String, Object> message(Message message) {
        var parts = new ArrayList<Map<String, Object>>();
        if (message instanceof ToolResponseMessage tool) {
            for (var r : tool.getResponses()) {
                var part = new LinkedHashMap<String, Object>();
                part.put("type", "tool_call_response");
                part.put("id", r.id());
                part.put("name", r.name());
                part.put("response", r.responseData());
                parts.add(part);
            }
        } else {
            var text = message.getText();
            if (text != null && !text.isEmpty()) {
                parts.add(text(text));
            }
            if (message instanceof AssistantMessage assistant && assistant.getToolCalls() != null) {
                for (var call : assistant.getToolCalls()) {
                    var part = new LinkedHashMap<String, Object>();
                    part.put("type", "tool_call");
                    part.put("id", call.id());
                    part.put("name", call.name());
                    part.put("arguments", call.arguments());
                    parts.add(part);
                }
            }
        }
        var m = new LinkedHashMap<String, Object>();
        m.put("role", message.getMessageType().getValue());
        m.put("parts", parts);
        return m;
    }

    private static Map<String, Object> text(String text) {
        var part = new LinkedHashMap<String, Object>();
        part.put("type", "text");
        part.put("content", text);
        return part;
    }

    private static String json(List<?> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
