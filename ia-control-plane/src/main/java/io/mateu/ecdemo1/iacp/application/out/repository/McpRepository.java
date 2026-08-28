package io.mateu.ecdemo1.iacp.application.out.repository;

import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.Mcp;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpId;

public interface McpRepository extends Repository<Mcp, McpId> {
}
