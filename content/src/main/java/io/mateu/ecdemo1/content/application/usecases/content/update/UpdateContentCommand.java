package io.mateu.ecdemo1.content.application.usecases.content.update;

import io.mateu.ecdemo1.content.application.usecases.content.ContentValueDto;

import java.util.List;

public record UpdateContentCommand(String id, String name, String contentType, List<String> labels, List<ContentValueDto> values) {
}
