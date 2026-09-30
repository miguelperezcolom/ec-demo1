package io.mateu.ecdemo1.registration.application;

import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRequirements;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import io.mateu.ecdemo1.registration.store.RegistrationRule;
import io.mateu.ecdemo1.registration.store.RegistrationRuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * The registration rules: which of a guest's data each destination's law requires when the hotel
 * registers them. Governance, so they are kept here, in the control plane, by compliance or the
 * central office — not by a hotel's desk — and every change is audited (who, when, what). Each change
 * is published whole on {@code registration-rules}, where every front office keeps its copy and
 * applies it, with or without the network: the desk, the reception agent and an online check-in alike.
 */
@Service
@Slf4j
public class RegistrationRules {

    public static final String SERVICE = "registration-rules";

    /** What a person (or the agent) asks for. */
    public record Draft(String name, Scope scope, String scopeCode, NationalityMatch nationalityMatch,
                        List<String> nationalities, Integer minAge, Integer maxAge, Role role,
                        Set<Field> requiredFields, Set<Field> exemptFields, Set<Moment> moments,
                        String legalBasis, LocalDate from, LocalDate to, boolean active) {
    }

    /** A guest to test the rules against («Evaluar»): who, where and when. */
    public record Probe(String hotelCode, Moment moment, LocalDate day, Role role, String nationality,
                        LocalDate birthDate) {
    }

    /** Where every change goes: the outbox, and from it the registration-rules and audit topics. */
    public interface Events {
        void publish(RegistrationRuleChanged event);

        void audit(AuditedAction action);
    }

    /** The country of each CRS hotel — until a master has it. */
    public record HotelCountries(Map<String, String> byHotel) {
        public String of(String hotelCode) {
            return hotelCode == null ? null : byHotel.get(hotelCode.trim().toUpperCase(Locale.ROOT));
        }
    }

    final RegistrationRuleRepository rules;
    final Events events;
    final HotelCountries countries;
    final Clock clock;

    public RegistrationRules(RegistrationRuleRepository rules, Events events, HotelCountries countries, Clock clock) {
        this.rules = rules;
        this.events = events;
        this.countries = countries;
        this.clock = clock;
    }

    @Transactional
    public RegistrationRule create(Draft draft, String by) {
        var r = new RegistrationRule();
        r.id = "RR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        r.createdAt = clock.instant();
        r.version = 0;
        apply(r, draft);
        touch(r, by, "Registration rule created");
        log.info("Registration rule {} ({} {}) created by {}", r.id, r.scope, r.scopeCode, by);
        return r;
    }

    @Transactional
    public RegistrationRule update(String id, Draft draft, String by) {
        var r = find(id);
        apply(r, draft);
        touch(r, by, "Registration rule changed");
        log.info("Registration rule {} v{} changed by {}", r.id, r.version, by);
        return r;
    }

    @Transactional
    public RegistrationRule setActive(String id, boolean active, String by) {
        var r = find(id);
        r.active = active;
        touch(r, by, active ? "Registration rule activated" : "Registration rule deactivated");
        return r;
    }

    void apply(RegistrationRule r, Draft d) {
        if (d.scope() == null) {
            throw new IllegalArgumentException("Indica dónde aplica: a los hoteles de un país o a un hotel");
        }
        if (d.scopeCode() == null || d.scopeCode().isBlank()) {
            throw new IllegalArgumentException(d.scope() == Scope.COUNTRY
                    ? "Indica el país (código ISO de dos letras, p. ej. ES)" : "Indica el hotel (código del CRS, p. ej. MRU01)");
        }
        var code = d.scopeCode().trim().toUpperCase(Locale.ROOT);
        if (d.scope() == Scope.COUNTRY && !code.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("El país va como código ISO de dos letras (ES, MU…), no «" + code + "»");
        }
        if (d.requiredFields() == null || d.requiredFields().isEmpty()) {
            throw new IllegalArgumentException("Indica qué datos exige la regla");
        }
        if (d.moments() == null || d.moments().isEmpty()) {
            throw new IllegalArgumentException("Indica cuándo se piden: antes de la llegada, en el check-in o en el check-in online");
        }
        var match = d.nationalityMatch() == null ? NationalityMatch.ANY : d.nationalityMatch();
        var nationalities = d.nationalities() == null ? List.<String>of()
                : d.nationalities().stream().filter(s -> s != null && !s.isBlank()).map(s -> s.trim().toUpperCase(Locale.ROOT)).toList();
        if (match != NationalityMatch.ANY && nationalities.isEmpty()) {
            throw new IllegalArgumentException("Indica las nacionalidades (códigos ISO, o EU para la Unión Europea)");
        }
        if (d.minAge() != null && d.maxAge() != null && d.maxAge() < d.minAge()) {
            throw new IllegalArgumentException("La edad máxima es menor que la mínima");
        }
        if (d.from() != null && d.to() != null && d.to().isBefore(d.from())) {
            throw new IllegalArgumentException("La fecha hasta es anterior a la fecha desde");
        }
        if (d.scope() == Scope.COUNTRY && d.exemptFields() != null && !d.exemptFields().isEmpty()) {
            throw new IllegalArgumentException("Solo una regla de hotel puede eximir de datos que pide su país");
        }
        r.name = d.name() == null || d.name().isBlank() ? d.scope() + " " + code : d.name().trim();
        r.scope = d.scope().name();
        r.scopeCode = code;
        r.nationalityMatch = match.name();
        r.nationalities = RegistrationRule.join(match == NationalityMatch.ANY ? List.of() : nationalities);
        r.minAge = d.minAge();
        r.maxAge = d.maxAge();
        r.role = (d.role() == null ? Role.ANY : d.role()).name();
        r.requiredFields = RegistrationRule.join(d.requiredFields().stream().sorted().toList());
        r.exemptFields = RegistrationRule.join(d.exemptFields() == null ? List.of() : d.exemptFields().stream().sorted().toList());
        r.moments = RegistrationRule.join(d.moments().stream().sorted().toList());
        r.legalBasis = d.legalBasis() == null || d.legalBasis().isBlank() ? null : d.legalBasis().trim();
        r.fromDate = d.from();
        r.toDate = d.to();
        r.active = d.active();
    }

    void touch(RegistrationRule r, String by, String action) {
        r.version++;
        r.updatedAt = clock.instant();
        r.updatedBy = by;
        rules.save(r);
        var event = event(r);
        events.publish(event);
        var params = new LinkedHashMap<String, Object>();
        params.put("ruleId", r.id);
        params.put("scope", r.scope + ":" + r.scopeCode);
        params.put("version", r.version);
        params.put("active", r.active);
        events.audit(new AuditedAction(UUID.randomUUID().toString(), clock.instant(), SERVICE, action,
                r.scope() == Scope.HOTEL ? r.scopeCode : null, by == null || by.isBlank() ? "consola" : by,
                json(params), true, r.name + " · v" + r.version));
    }

    public RegistrationRule find(String id) {
        return rules.findById(id).orElseThrow(() -> new NoSuchElementException("No existe la regla " + id));
    }

    public List<RegistrationRule> all() {
        return rules.findAll();
    }

    /** What the rules require of a guest like that — exactly what the front office would ask of them. */
    public RegistrationRequirements.Result evaluate(Probe p) {
        var hotel = p.hotelCode() == null ? null : p.hotelCode().trim().toUpperCase(Locale.ROOT);
        var guest = new RegistrationRequirements.Guest(hotel, countries.of(hotel),
                p.moment() == null ? Moment.CHECK_IN : p.moment(), p.day() == null ? LocalDate.now(clock) : p.day(),
                p.role() == null ? Role.HOLDER : p.role(),
                p.nationality() == null || p.nationality().isBlank() ? null : p.nationality().trim().toUpperCase(Locale.ROOT),
                p.birthDate());
        return RegistrationRequirements.of(all().stream().map(this::event).toList(), guest);
    }

    public String countryOf(String hotelCode) {
        return countries.of(hotelCode);
    }

    /** Every rule published again, as it is: for a reader that lost its copy (a new front office's database). */
    @Transactional
    public int republishAll() {
        var all = rules.findAll();
        all.forEach(r -> events.publish(event(r)));
        log.info("{} registration rule(s) published again", all.size());
        return all.size();
    }

    public RegistrationRuleChanged event(RegistrationRule r) {
        return new RegistrationRuleChanged(UUID.randomUUID().toString(), clock.instant(), r.id, r.version, r.name,
                r.scope(), r.scopeCode, r.nationalityMatch(), r.nationalityList(), r.minAge, r.maxAge, r.role(),
                r.requiredList(), r.exemptList(), r.momentList(), r.legalBasis, r.fromDate, r.toDate, r.active);
    }

    static String json(Map<String, Object> params) {
        var sb = new StringBuilder("{");
        params.forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"').append(k).append("\":");
            sb.append(v instanceof Number || v instanceof Boolean ? String.valueOf(v)
                    : "\"" + String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        });
        return sb.append('}').toString();
    }
}
