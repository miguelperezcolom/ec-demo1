package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

/**
 * How the aggregates are written as documents: by their fields — the domain has no setters and no
 * annotations, and keeps it that way — and an audience by an {@code op} tag, through a mix-in.
 */
public final class Json {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Audience.Everyone.class, name = "everyone"),
            @JsonSubTypes.Type(value = Audience.AllOf.class, name = "all"),
            @JsonSubTypes.Type(value = Audience.AnyOf.class, name = "any"),
            @JsonSubTypes.Type(value = Audience.AttributeIn.class, name = "in"),
            @JsonSubTypes.Type(value = Audience.Named.class, name = "named")})
    interface AudienceMixin {
    }

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new Jdk8Module())
            .visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .addMixIn(Audience.class, AudienceMixin.class)
            .build();

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write " + value, e);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return json == null ? null : MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read a " + type.getSimpleName() + ": " + json, e);
        }
    }

    public static <T> T read(String json, TypeReference<T> type) {
        try {
            return json == null ? null : MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read " + json, e);
        }
    }

    public static Audience audience(String json) {
        return read(json, Audience.class);
    }
}
