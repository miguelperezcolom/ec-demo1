package io.mateu.ecdemo1.iacp.application.usecases.apimcp.update;

import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;

/** No credential and no tools: both change through use cases of their own. */
public record UpdateApiMcpCommand(String id, String name, ApiKind kind, String baseUrl,
                                  String specUrl, String description, boolean enabled) {
}
