package io.mateu.ecdemo1.customerhistory.infra.in.async;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * The variant of a polymorphic topic's message, by its {@code type} discriminator, read before the
 * message itself: a variant this service does not take is skipped without binding it — and a variant
 * added tomorrow, which no class here names, is skipped rather than failing as an unknown subtype.
 */
final class Payloads {

    private Payloads() {
    }

    /** The {@code type} of a JSON object; "" if it has none; null if it is not JSON. */
    static String type(ObjectMapper reader, byte[] payload) {
        try {
            var tree = reader.readTree(payload);
            if (tree == null || !tree.isObject()) {
                return null;
            }
            return tree.path("type").asText("");
        } catch (IOException e) {
            return null;
        }
    }
}
