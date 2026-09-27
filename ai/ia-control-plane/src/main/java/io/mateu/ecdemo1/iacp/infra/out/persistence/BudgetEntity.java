package io.mateu.ecdemo1.iacp.infra.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** The row behind a {@code Budget}. */
@Entity
@Table(name = "budget")
@Getter
@Setter
@NoArgsConstructor
public class BudgetEntity {

    @Id
    String id;
    String name;
    String scope;
    String subjectId;
    String period;
    long limitTokens;
    boolean enabled;
    LocalDateTime created;
}
