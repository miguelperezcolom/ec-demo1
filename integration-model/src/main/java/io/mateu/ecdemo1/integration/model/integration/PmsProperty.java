package io.mateu.ecdemo1.integration.model.integration;

/** A property of the chain in the PMS, as the enterprise lists them: what a hotel is integrated with. */
public record PmsProperty(String code, String name, String currency) {
}
