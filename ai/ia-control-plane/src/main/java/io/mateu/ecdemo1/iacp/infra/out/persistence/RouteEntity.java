package io.mateu.ecdemo1.iacp.infra.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The row behind a {@code Route}. Null condition columns mean "don't care".
 *
 * <p>The guardrails are comma-separated ids, like an agent's references (see {@code IdList}), and
 * the failure mode is a plain string: an enum column would get a check constraint that
 * {@code ddl-auto: update} never rewrites, so a value added later would break every database created
 * before it. Null reads as CLOSED, which is what a route stored before guardrails existed means.
 */
@Entity
@Table(name = "route")
@Getter
@Setter
@NoArgsConstructor
public class RouteEntity {

    @Id
    String id;
    String name;
    int priority;
    String role;
    String tenant;
    String locale;
    String routePrefix;
    /** data-plane, control-plane or front-office; null matches any. A String, not an enum column. */
    String channel;
    String targetAgentId;
    @Column(length = 4096)
    String inputGuardrailAgentIds;
    @Column(length = 4096)
    String outputGuardrailAgentIds;
    String guardrailFailure;
    boolean enabled;
    LocalDateTime created;
}
