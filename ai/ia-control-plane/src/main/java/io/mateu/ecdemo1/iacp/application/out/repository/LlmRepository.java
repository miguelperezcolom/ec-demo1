package io.mateu.ecdemo1.iacp.application.out.repository;

import io.mateu.ecdemo1.iacp.domain.aggregates.llm.Llm;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;

public interface LlmRepository extends Repository<Llm, LlmId> {
}
