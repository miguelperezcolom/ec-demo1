package io.mateu.ecdemo1.content.application.usecases.label.delete;

import java.util.List;

public record DeleteLabelCommand(List<String> ids) {
    }
