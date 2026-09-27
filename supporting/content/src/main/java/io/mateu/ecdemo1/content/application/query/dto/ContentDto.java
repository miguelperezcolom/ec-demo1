package io.mateu.ecdemo1.content.application.query.dto;

import io.mateu.ecdemo1.content.application.usecases.content.ContentValueDto;

import java.util.List;

public record ContentDto(String id,
                         String name,
                         String contentType,
                         List<String> labels,
                         List<ContentValueDto> values) {
}
