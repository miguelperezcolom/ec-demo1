package io.mateu.ecdemo1.iacp.infra.out.gitops;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A row that records git owns one catalogue entry. Provenance and nothing else — no copy of the
 * entry, which lives in its own catalogue table; just the fact that git, not the console, put it
 * there.
 *
 * <p>The primary key is {@code kind/id} composed into one string rather than a composite key,
 * because the two questions asked of this table — "is this one git's?" and "which of this kind are
 * git's?" — are an exists-by-key and a lookup-by-kind, and both are simpler against a single-column
 * id with an index on {@code kind}.
 */
@Entity
@Table(name = "gitops_managed", indexes = @Index(name = "idx_gitops_managed_kind", columnList = "kind"))
@Getter
@Setter
@NoArgsConstructor
public class GitopsManagedEntity {

    /** {@code kind + "/" + entryId}, e.g. {@code llm/anthropic}. */
    @Id
    String pk;

    String kind;

    String entryId;

    public GitopsManagedEntity(String kind, String entryId) {
        this.kind = kind;
        this.entryId = entryId;
        this.pk = kind + "/" + entryId;
    }
}
