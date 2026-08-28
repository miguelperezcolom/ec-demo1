package io.mateu.ecdemo1.content.application.usecases.content.create;

import io.mateu.ecdemo1.content.application.usecases.content.ContentValueDto;

import java.util.List;

public record CreateContentCommand(String name, String contentType, List<String> labels, List<ContentValueDto> values) {
}
