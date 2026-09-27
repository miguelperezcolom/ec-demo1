package io.mateu.ecdemo1.mapping.store;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * One version of the equivalence of one CRS code. {@code hotelCode} null is a chain-level entry;
 * set, it is an exception for that property, and wins over the chain's (F009, two levels).
 */
@Entity
@Table(name = "mapping_entry", indexes = @Index(columnList = "type, sourceCode, hotelCode, status"))
@NoArgsConstructor
@Getter
@Setter
public class MappingEntry {

    @Id
    public String id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CodeType type;
    public String hotelCode;
    @Column(nullable = false)
    public String sourceCode;
    public String targetCode;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, String> attributes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EntryStatus status;
    /** 1 for the first approved version of this code in this scope, and one more with each. */
    public int entryVersion;
    /** Who proposed it: a person's name, or "agent". */
    public String proposedBy;
    /** 0..1, when the agent proposed it. */
    public Double confidence;
    @Column(length = 2000)
    public String rationale;
    /** Who approved or rejected it. */
    public String decidedBy;
    public Instant decidedAt;
    public Instant createdAt;

    public String scope() {
        return hotelCode == null ? "chain" : hotelCode;
    }
}
