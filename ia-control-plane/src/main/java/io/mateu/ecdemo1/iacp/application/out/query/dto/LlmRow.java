package io.mateu.ecdemo1.iacp.application.out.query.dto;

public record LlmRow(String id, String name, String provider, String model,
                     String credential, String status) {
}
