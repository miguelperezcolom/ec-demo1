package io.mateu.ecdemo1.registration.infra.in.mcp;

import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.NationalityMatch;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Role;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Scope;
import io.mateu.ecdemo1.registration.application.RegistrationRules;
import io.mateu.ecdemo1.registration.infra.in.rest.RuleView;
import io.mateu.workflow.mcp.McpSystemContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The registration rules, for the control plane's agent: read them, test a guest against them, and
 * draft one — created inactive, for a person to review and activate: a legal requirement is not
 * put in force by an agent. Every tool answers a list of views or a sentence, never Object.
 */
@Component
public class RuleMcpTools implements McpSystemContext {

    final RegistrationRules rules;

    public RuleMcpTools(RegistrationRules rules) {
        this.rules = rules;
    }

    @Override
    public String getSystemContext() {
        return """
                Reglas de registro de huéspedes (kárdex):
                - Una regla dice qué datos del huésped exige la ley de un destino al registrarlo: por país del
                  hotel (scope COUNTRY, código ISO: ES, MU…) o por hotel (scope HOTEL, código del CRS: MRU01).
                - Se aplica según la nacionalidad (ANY, IN o NOT_IN una lista; EU es la Unión Europea), la
                  edad el día de llegada (minAge/maxAge) y el rol (HOLDER, COMPANION o ANY), en unos momentos
                  (PRE_ARRIVAL, CHECK_IN, ONLINE_CHECK_IN) y unas fechas.
                - Datos posibles: DOCUMENT_TYPE, DOCUMENT_NUMBER, DOCUMENT_ISSUING_COUNTRY, DOCUMENT_EXPIRY,
                  BIRTH_DATE, BIRTH_PLACE, NATIONALITY, SEX, ADDRESS, CITY, POSTAL_CODE, COUNTRY_OF_RESIDENCE,
                  SIGNATURE, GUARDIAN (adulto responsable y parentesco, para menores).
                - Lo que se exige a un huésped es la suma de las reglas que le aplican, menos lo que exima una
                  regla de su hotel. El front office lo aplica en el check-in: sin esos datos, no se hace.
                - Tú puedes consultarlas, evaluarlas y proponer una en borrador (inactiva); la activa una persona.
                """;
    }

    @Tool(description = "List the registration rules: scope, whom they apply to, what they require, when")
    public List<RuleView> listRegistrationRules() {
        return rules.all().stream().map(RuleView::of).toList();
    }

    @Tool(description = "What the registration rules require of a guest: hotelCode (CRS, e.g. MRU01), nationality "
            + "(ISO alpha-2), birthDate (yyyy-MM-dd, may be empty), role (HOLDER or COMPANION), moment (CHECK_IN, "
            + "PRE_ARRIVAL or ONLINE_CHECK_IN; empty, CHECK_IN), day (yyyy-MM-dd; empty, today)")
    public String evaluateRegistrationRules(String hotelCode, @ToolParam(required = false) String nationality,
                                            @ToolParam(required = false) String birthDate,
                                            @ToolParam(required = false) String role,
                                            @ToolParam(required = false) String moment,
                                            @ToolParam(required = false) String day) {
        var result = rules.evaluate(new RegistrationRules.Probe(hotelCode, blank(moment) ? null : Moment.valueOf(moment.trim()),
                blank(day) ? null : LocalDate.parse(day.trim()), blank(role) ? null : Role.valueOf(role.trim()),
                nationality, blank(birthDate) ? null : LocalDate.parse(birthDate.trim())));
        if (result.fields().isEmpty()) {
            return "Ninguna regla exige datos a ese huésped en " + hotelCode + ".";
        }
        return "Exigen: " + String.join(", ", result.fields().stream()
                .map(io.mateu.ecdemo1.integration.model.registration.RegistrationRequirements::label).toList())
                + ". Base legal: " + String.join("; ", result.legalBases()) + ". Reglas: " + String.join(", ", result.ruleIds());
    }

    @Tool(description = "Draft a registration rule, INACTIVE until a person activates it. scope COUNTRY or HOTEL; "
            + "scopeCode the ISO country or the CRS hotel; nationalityMatch ANY, IN or NOT_IN; nationalities comma "
            + "separated (EU allowed); requiredFields and moments comma separated names; role HOLDER, COMPANION or ANY")
    public String draftRegistrationRule(String name, String scope, String scopeCode,
                                        @ToolParam(required = false) String nationalityMatch,
                                        @ToolParam(required = false) String nationalities,
                                        @ToolParam(required = false) Integer minAge,
                                        @ToolParam(required = false) Integer maxAge,
                                        @ToolParam(required = false) String role,
                                        String requiredFields, String moments,
                                        @ToolParam(required = false) String legalBasis) {
        try {
            var r = rules.create(new RegistrationRules.Draft(name, Scope.valueOf(scope.trim().toUpperCase(Locale.ROOT)), scopeCode,
                    blank(nationalityMatch) ? NationalityMatch.ANY : NationalityMatch.valueOf(nationalityMatch.trim()),
                    blank(nationalities) ? List.of() : Arrays.stream(nationalities.split(",")).map(String::trim).toList(),
                    minAge, maxAge, blank(role) ? Role.ANY : Role.valueOf(role.trim()), enums(requiredFields, Field.class),
                    Set.of(), enums(moments, Moment.class), legalBasis, null, null, false), "control-plane-agent");
            return "Regla " + r.id + " creada en borrador (inactiva): la activa una persona desde la consola.";
        } catch (IllegalArgumentException e) {
            return "No se ha creado: " + e.getMessage();
        }
    }

    static <E extends Enum<E>> Set<E> enums(String value, Class<E> type) {
        var set = EnumSet.noneOf(type);
        if (!blank(value)) {
            Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(s -> set.add(Enum.valueOf(type, s.toUpperCase(Locale.ROOT))));
        }
        return set;
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
