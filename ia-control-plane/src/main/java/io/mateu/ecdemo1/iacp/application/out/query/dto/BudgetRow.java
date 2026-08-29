package io.mateu.ecdemo1.iacp.application.out.query.dto;

public record BudgetRow(String id, String name, String scope, String subject, String period,
                        long limitTokens, String status) {
}
