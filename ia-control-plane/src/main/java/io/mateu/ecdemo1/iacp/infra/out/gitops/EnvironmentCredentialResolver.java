package io.mateu.ecdemo1.iacp.infra.out.gitops;

import io.mateu.ecdemo1.iacp.application.out.gitops.CredentialResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolves the env-var name an LLM entry carries against this pod's own environment. Spring's
 * {@link Environment} is asked first — it already unifies OS environment variables with everything
 * else the context was configured from — and the raw {@code System.getenv} is the fallback for a
 * name Spring's relaxed binding would not have matched.
 */
@Component
@ConditionalOnProperty(name = "cp.gitops.enabled", havingValue = "true")
@RequiredArgsConstructor
public class EnvironmentCredentialResolver implements CredentialResolver {

    private final Environment environment;

    @Override
    public String resolve(String envName) {
        var value = environment.getProperty(envName);
        return value != null ? value : System.getenv(envName);
    }
}
