package io.mateu.ecdemo1.content.application.usecases.content.delete;

import java.util.List;

public record DeleteContentCommand(List<String> ids) {
    }
