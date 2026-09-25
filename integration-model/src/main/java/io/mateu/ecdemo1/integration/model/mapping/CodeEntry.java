package io.mateu.ecdemo1.integration.model.mapping;

/** One code of one side's catalog, with its description — what the mapping pairs. */
public record CodeEntry(CodeType type, String hotelCode, String code, String description) {
}
