package io.mateu.ecdemo1.iacp.infra.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** The row behind a {@code Route}. Null condition columns mean "don't care". */
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
    String targetAgentId;
    boolean enabled;
    LocalDateTime created;
}
