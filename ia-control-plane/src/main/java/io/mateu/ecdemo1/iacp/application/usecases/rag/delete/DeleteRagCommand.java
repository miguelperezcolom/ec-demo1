package io.mateu.ecdemo1.iacp.application.usecases.rag.delete;

import java.util.List;

public record DeleteRagCommand(List<String> ids) {
}
