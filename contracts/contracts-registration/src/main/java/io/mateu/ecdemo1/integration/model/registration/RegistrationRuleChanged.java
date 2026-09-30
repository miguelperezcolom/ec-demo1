package io.mateu.ecdemo1.integration.model.registration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A registration rule — which of a guest's data a destination's law requires when the hotel registers
 * them — as it is now: created, changed or deactivated. Published by the registration-rules service
 * (the control plane, where compliance keeps them) on the {@code registration-rules} topic, keyed by
 * its scope ({@link #key()}); the front office keeps a copy and applies it at check-in, in the kárdex
 * and to a walk-in, with or without the network. The whole rule each time: a reader keeps the one with
 * the highest {@code version} and asks for nothing.
 *
 * <p>A rule applies to a guest when its scope is the hotel's (its country, or the hotel itself), the
 * day is within {@code from}–{@code to}, the guest's nationality matches ({@code nationalityMatch}
 * over {@code nationalities}; {@code EU} stands for the member states), their age on the arrival day
 * is within {@code minAge}–{@code maxAge} (either may be null), and their role is {@code role}. The
 * fields required of a guest are the union of {@code requiredFields} of every rule that applies, less
 * the {@code exemptFields} of an applying HOTEL rule — a hotel can relax its country's rule, not the
 * other way round.
 *
 * @param ruleId           stable for the rule's whole life
 * @param version          grows with every change: an older one never replaces a newer
 * @param name             how compliance names it
 * @param scope            a country's hotels, or one hotel
 * @param scopeCode        the ISO 3166-1 alpha-2 country (ES, MU) or the CRS hotel code (MRU01)
 * @param nationalityMatch whom by nationality: any, those in {@code nationalities}, those not in them
 * @param nationalities    ISO alpha-2 codes, and {@code EU} for the member states
 * @param minAge           the youngest it applies to, in whole years on the arrival day; null, no minimum
 * @param maxAge           the oldest; null, no maximum
 * @param role             the holder, a companion, or any guest
 * @param requiredFields   what it requires
 * @param exemptFields     what a HOTEL rule waives of its country's rules; ignored on a COUNTRY rule
 * @param moments          when it is asked for: preparing the arrival, at the desk's check-in, online
 * @param legalBasis       why, as the desk reads it (e.g. «RD 933/2021 · SES.Hospedajes»)
 * @param from             the first day it applies; null, since always
 * @param to               the last day; null, with no end
 * @param active           false once deactivated: a reader stops applying it
 */
public record RegistrationRuleChanged(String eventId, Instant occurredAt, String ruleId, long version,
                                      String name, Scope scope, String scopeCode,
                                      NationalityMatch nationalityMatch, List<String> nationalities,
                                      Integer minAge, Integer maxAge, Role role,
                                      List<Field> requiredFields, List<Field> exemptFields, List<Moment> moments,
                                      String legalBasis, LocalDate from, LocalDate to, boolean active) {

    public static final String TOPIC = "registration-rules";

    /** Where it applies. */
    public enum Scope { COUNTRY, HOTEL }

    /** Whom it applies to by nationality. */
    public enum NationalityMatch { ANY, IN, NOT_IN }

    /** Which guests of a stay. */
    public enum Role { ANY, HOLDER, COMPANION }

    /** When it is asked for. */
    public enum Moment { PRE_ARRIVAL, CHECK_IN, ONLINE_CHECK_IN }

    /** What a destination may require of a guest's registration. */
    public enum Field {
        DOCUMENT_TYPE, DOCUMENT_NUMBER, DOCUMENT_ISSUING_COUNTRY, DOCUMENT_EXPIRY,
        BIRTH_DATE, BIRTH_PLACE, NATIONALITY, SEX,
        ADDRESS, CITY, POSTAL_CODE, COUNTRY_OF_RESIDENCE,
        SIGNATURE,
        /** For a minor: the adult responsible for them and how they are related. */
        GUARDIAN
    }

    /** The Kafka key: the scope, {@code COUNTRY:ES} or {@code HOTEL:MRU01}. */
    public String key() {
        return scope + ":" + scopeCode;
    }
}
