package io.mateu.ecdemo1.registration.infra.in.rest;

import io.mateu.ecdemo1.registration.store.RegistrationRule;

import java.time.LocalDate;
import java.util.List;

/** A rule as the REST API and the agent's tools show it. */
public record RuleView(String id, long version, String name, String scope, String scopeCode, String nationalityMatch,
                       List<String> nationalities, Integer minAge, Integer maxAge, String role,
                       List<String> requiredFields, List<String> exemptFields, List<String> moments,
                       String legalBasis, LocalDate from, LocalDate to, boolean active) {

    public static RuleView of(RegistrationRule r) {
        return new RuleView(r.id, r.version, r.name, r.scope, r.scopeCode, r.nationalityMatch().name(),
                r.nationalityList(), r.minAge, r.maxAge, r.role().name(),
                r.requiredList().stream().map(Enum::name).toList(), r.exemptList().stream().map(Enum::name).toList(),
                r.momentList().stream().map(Enum::name).toList(), r.legalBasis, r.fromDate, r.toDate, r.active);
    }
}
