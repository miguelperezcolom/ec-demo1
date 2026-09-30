package io.mateu.ecdemo1.registration.infra.in.rest;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRequirements;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import io.mateu.ecdemo1.registration.application.RegistrationRules;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * The rules over HTTP, inside the cluster only — the gateway routes the service's screens
 * (/_registration-rules), not this: listed, created (the demo's seed uses it), evaluated against a
 * guest, and published again whole for a reader that lost its copy.
 */
@RestController
@RequestMapping("/rules")
public class RulesController {

    /** A rule as the seed or a script sends it. */
    public record RuleRequest(String name, Scope scope, String scopeCode, NationalityMatch nationalityMatch,
                              List<String> nationalities, Integer minAge, Integer maxAge, Role role,
                              Set<Field> requiredFields, Set<Field> exemptFields, Set<Moment> moments,
                              String legalBasis, LocalDate from, LocalDate to, Boolean active) {
    }

    /** What is required of a guest, field by field in the desk's words, and why. */
    public record Evaluation(String hotelCountry, List<String> required, List<String> labels, List<String> legalBases,
                             List<String> ruleIds) {
    }

    final RegistrationRules rules;

    public RulesController(RegistrationRules rules) {
        this.rules = rules;
    }

    @GetMapping
    public List<RuleView> list() {
        return rules.all().stream().sorted(Comparator.comparing(r -> r.scope + r.scopeCode + r.id))
                .map(RuleView::of).toList();
    }

    @PostMapping
    public RuleView create(@RequestBody RuleRequest r, @RequestHeader(value = "X-User-Name", required = false) String by) {
        return RuleView.of(rules.create(new RegistrationRules.Draft(r.name(), r.scope(), r.scopeCode(),
                r.nationalityMatch(), r.nationalities(), r.minAge(), r.maxAge(), r.role(), r.requiredFields(),
                r.exemptFields(), r.moments(), r.legalBasis(), r.from(), r.to(), r.active() == null || r.active()),
                by == null || by.isBlank() ? "api" : by));
    }

    @PostMapping("/evaluate")
    public Evaluation evaluate(@RequestBody RegistrationRules.Probe probe) {
        return evaluation(rules.countryOf(probe.hotelCode()), rules.evaluate(probe));
    }

    static Evaluation evaluation(String country, RegistrationRequirements.Result result) {
        return new Evaluation(country, result.fields().stream().map(Enum::name).toList(),
                result.fields().stream().map(RegistrationRequirements::label).toList(), result.legalBases(),
                result.ruleIds());
    }

    @PostMapping("/republish")
    public Map<String, Integer> republish() {
        return Map.of("published", rules.republishAll());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ProblemDetail refused(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }
}
