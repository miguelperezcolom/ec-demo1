package io.mateu.ecdemo1.iaagent.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Jackson 2 mapper this service's own code asks for.
 *
 * <p>Boot 4 speaks Jackson 3 — {@code tools.jackson} — and auto-configures a mapper of that type
 * for HTTP conversion. It no longer contributes a {@code com.fasterxml.jackson.databind}
 * ObjectMapper, which is the one half a dozen classes here inject and the one the OpenAI and
 * Anthropic SDKs still bring along. Without this bean the context fails to start on
 * {@code IaAgentController} and says only that a bean is missing.
 *
 * <p>Declared rather than ported: what these classes do with it is read the control plane's JSON
 * and a JWT payload, and moving that to Jackson 3 would be a change to every one of them for no
 * behaviour anyone can see. Both versions are on the classpath and neither is in the other's way —
 * HTTP conversion is Jackson 3's, this is ours.
 */
@Configuration
public class JacksonConfig {

    /**
     * Unknown properties are ignored on purpose: the control plane is deployed independently and
     * may add a field before this service knows about it. That is a wire contract between two
     * services, not a schema they share.
     */
    @Bean
    ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
