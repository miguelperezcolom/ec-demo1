package io.mateu.ecdemo1.iacp.application.usecases.llm.delete;

import java.util.List;

public record DeleteLlmCommand(List<String> ids) {
}
