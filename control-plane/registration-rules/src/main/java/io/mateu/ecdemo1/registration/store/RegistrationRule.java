package io.mateu.ecdemo1.registration.store;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * A registration rule: which of a guest's data a destination requires, and of whom.
 *
 * <p>Enums and lists are kept as plain strings (lists comma separated), not as JPA enums: Hibernate's
 * {@code ddl-auto: update} writes a check constraint for an enum column and never rewrites it, so a
 * value added later would be refused by every database created before it.
 */
@Entity
@Table(name = "registration_rule", indexes = @Index(name = "registration_rule_scope", columnList = "scope, scopeCode"))
public class RegistrationRule {

    @Id
    public String id;

    @Column(length = 120)
    public String name;

    @Column(length = 10, nullable = false)
    public String scope;

    /** ISO country (ES) or CRS hotel (MRU01). */
    @Column(length = 20, nullable = false)
    public String scopeCode;

    @Column(length = 10)
    public String nationalityMatch;

    /** ISO codes and {@code EU}, comma separated. */
    @Column(length = 400)
    public String nationalities;

    public Integer minAge;

    public Integer maxAge;

    @Column(length = 12)
    public String role;

    /** {@link Field} names, comma separated. */
    @Column(length = 400)
    public String requiredFields;

    @Column(length = 400)
    public String exemptFields;

    /** {@link Moment} names, comma separated. */
    @Column(length = 80)
    public String moments;

    @Column(length = 300)
    public String legalBasis;

    public LocalDate fromDate;

    public LocalDate toDate;

    public boolean active;

    /** Grows with every change: what the front office orders them by. A default, for rows before it. */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    public long version;

    @Column(length = 120)
    public String updatedBy;

    public Instant createdAt;

    public Instant updatedAt;

    public Scope scope() {
        return Scope.valueOf(scope);
    }

    public NationalityMatch nationalityMatch() {
        return nationalityMatch == null ? NationalityMatch.ANY : NationalityMatch.valueOf(nationalityMatch);
    }

    public Role role() {
        return role == null ? Role.ANY : Role.valueOf(role);
    }

    public List<String> nationalityList() {
        return split(nationalities).stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
    }

    public List<Field> requiredList() {
        return enums(requiredFields, Field.class);
    }

    public List<Field> exemptList() {
        return enums(exemptFields, Field.class);
    }

    public List<Moment> momentList() {
        return enums(moments, Moment.class);
    }

    public static String join(Collection<?> values) {
        return values == null ? "" : values.stream().map(v -> v instanceof Enum<?> e ? e.name() : String.valueOf(v))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(","));
    }

    static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of()
                : Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** The names a stored value holds; one it does not know is left out rather than failing the rule. */
    static <E extends Enum<E>> List<E> enums(String value, Class<E> type) {
        return split(value).stream().map(n -> {
            try {
                return Enum.valueOf(type, n);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).filter(java.util.Objects::nonNull).sorted().toList();
    }
}
