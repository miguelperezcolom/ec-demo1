package io.mateu.ecdemo1.content.domain.aggregates.content.vo;


public record ContentName(String name) {

public ContentName {
if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
}
}
