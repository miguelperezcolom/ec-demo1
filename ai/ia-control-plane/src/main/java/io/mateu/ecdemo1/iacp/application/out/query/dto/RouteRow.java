package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.uidl.data.Status;

public record RouteRow(String id, String name, int priority, String targetAgentId, Status status) {
}
