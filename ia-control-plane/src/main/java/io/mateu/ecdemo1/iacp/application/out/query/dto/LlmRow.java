package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.uidl.data.Status;

public record LlmRow(String id, String name, String provider, String model,
                     String credential, Status status) {
}
