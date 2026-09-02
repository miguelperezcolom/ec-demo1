package io.mateu.ecdemo1.iacp.application.usecases.apimcp.create;

import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;

public record CreateApiMcpCommand(String id, String name, ApiKind kind, String baseUrl,
                                  String specUrl, String description) {
}
