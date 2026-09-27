package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.uidl.data.Status;

public record RagRow(String id, String name, String kind, String collection, Status status) {
}
