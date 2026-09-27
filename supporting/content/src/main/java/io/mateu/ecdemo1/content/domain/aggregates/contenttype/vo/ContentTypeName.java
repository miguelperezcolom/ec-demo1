package io.mateu.ecdemo1.content.domain.aggregates.contenttype.vo;


public record ContentTypeName(String name) {

public ContentTypeName {
if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
}
}
