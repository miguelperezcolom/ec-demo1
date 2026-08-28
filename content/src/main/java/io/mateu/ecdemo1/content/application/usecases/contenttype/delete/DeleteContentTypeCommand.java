package io.mateu.ecdemo1.content.application.usecases.contenttype.delete;

import java.util.List;

public record DeleteContentTypeCommand(List<String> ids) {
    }
