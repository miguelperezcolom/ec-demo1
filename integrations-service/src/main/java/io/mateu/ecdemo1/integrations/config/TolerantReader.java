package io.mateu.ecdemo1.integrations.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * A tolerant reader for what other systems send: a field another service adds tomorrow must not break the
 * integration today. The application's own mapper refuses unknown fields — right for its own API,
 * wrong for reading someone else's. A component and not an ObjectMapper bean, because declaring
 * one would switch Spring Boot's off.
 */
@Component
public class TolerantReader {

    private final ObjectMapper mapper;

    public TolerantReader(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
