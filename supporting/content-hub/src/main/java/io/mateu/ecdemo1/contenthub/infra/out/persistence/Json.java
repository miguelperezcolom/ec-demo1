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

    static JsonMapper.Builder builder() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addMixIn(Audience.class, AudienceMixin.class);
    }

    /** Blocks as they are, for {@link KnownBlocks}. */
    static final ObjectMapper BLOCKS = builder().build();

    /**
     * A block of a type this version does not know (written by a newer one: a branch, a rollback) is read as
     * nothing — logged and left out — instead of failing the whole item, and with it every listing that reads it.
     */
    static final class KnownBlocks extends com.fasterxml.jackson.databind.JsonDeserializer<io.mateu.ecdemo1.contenthub.domain.item.Block> {
        static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(KnownBlocks.class);

        @Override
        public io.mateu.ecdemo1.contenthub.domain.item.Block deserialize(com.fasterxml.jackson.core.JsonParser p,
                                                                       com.fasterxml.jackson.databind.DeserializationContext c)
                throws java.io.IOException {
            com.fasterxml.jackson.databind.JsonNode node = p.readValueAsTree();
            var type = node == null ? null : node.get("type");
            if (type == null || java.util.Arrays.stream(io.mateu.ecdemo1.contenthub.domain.item.BlockType.values())
                    .noneMatch(t -> t.name().equals(type.asText()))) {
                LOG.warn("Block {} of unknown type {} left out", node == null ? null : node.get("id"), type);
                return null;
            }
            return BLOCKS.treeToValue(node, io.mateu.ecdemo1.contenthub.domain.item.Block.class);
        }
    }

    public static final ObjectMapper MAPPER = builder()
            .addModule(new com.fasterxml.jackson.databind.module.SimpleModule("known-blocks")
                    .addDeserializer(io.mateu.ecdemo1.contenthub.domain.item.Block.class, new KnownBlocks()))
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
