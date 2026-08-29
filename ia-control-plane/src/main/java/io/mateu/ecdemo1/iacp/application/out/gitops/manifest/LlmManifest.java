package io.mateu.ecdemo1.iacp.application.out.gitops.manifest;

import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmProvider;

/**
 * One LLM entry as the repo declares it. The field that is conspicuously absent is the API key:
 * a repo, public or not, is the wrong place for a credential. Instead {@code credentialEnv} names
 * an environment variable, and the control plane resolves it from its own Secret at sync time — so
 * the repo says <em>which</em> secret, and only the deployment holds the secret itself.
 */
public record LlmManifest(String id, String name, LlmProvider provider, String model,
                          String baseUrl, Double temperature, Integer maxTokens,
                          String credentialEnv, Boolean enabled) {
}
