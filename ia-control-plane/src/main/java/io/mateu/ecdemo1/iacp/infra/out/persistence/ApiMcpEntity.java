package io.mateu.ecdemo1.iacp.infra.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * An API-backed MCP server as a row.
 *
 * <p>{@code toolsJson} is the exposed offer, serialised. It is stored as one document rather than a
 * child table on purpose: the offer is replaced whole by a single use case — see
 * {@code ApiMcp.exposeExactly} — so there is nothing to gain from rows that can be inserted and
 * deleted one at a time, and something to lose, which is an entry that is half of an old import.
 *
 * <p>{@code credential} holds ciphertext and only ciphertext. {@code @ToString} is Lombok's and
 * that is safe here only because the field never holds a plaintext secret; if that ever stops being
 * true this annotation has to go.
 */
@Entity
@Table(name = "api_mcp")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ApiMcpEntity {

    @Id
    String id;
    @Column(nullable = false)
    String name;
    @Column(nullable = false)
    String kind;
    @Column(nullable = false, length = 2048)
    String baseUrl;
    @Column(nullable = false, length = 2048)
    String specUrl;
    @Lob
    String credential;
    @Lob
    String toolsJson;
    @Column(length = 2048)
    String description;
    boolean enabled;
    LocalDateTime created;
}
