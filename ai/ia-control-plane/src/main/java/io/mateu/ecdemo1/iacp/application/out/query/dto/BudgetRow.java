package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.uidl.data.Status;

public record BudgetRow(String id, String name, String scope, String subject, String period,
                        long limitTokens, Status status) {
}
