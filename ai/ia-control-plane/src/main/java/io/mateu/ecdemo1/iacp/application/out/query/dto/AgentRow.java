package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.uidl.data.Status;

public record AgentRow(String id, String name, String llm, int mcps, int rags, int peers,
                       Status status) {
}
