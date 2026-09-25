package io.mateu.ecdemo1.integration.model.integration;

/** What the connector found when it tried a connection: a token, and a read on the property. */
public record ConnectivityCheck(boolean ok, String message) {
}
