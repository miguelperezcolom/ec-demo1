package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.uidl.data.Status;

public record McpRow(String id, String name, String url, String transport, Status status) {
}
