package io.mateu.ecdemo1.mdm.marking;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.Customer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a customer's Salesforce contact is marked: its contacts keep being created at booking — as
 * Opera's profile is — but each says how far it can be trusted. Computed by the MDM, which owns the
 * golden record, and projected with it; nobody sets it by hand.
 *
 * <ul>
 *   <li><b>Estado MDM</b>: Provisional, Consolidado — or Anonimizado, once its personal data were erased.</li>
 *   <li><b>Calidad del dato</b>: Solo nombre (nothing to reach or identify the person by), Con contacto (an
 *       email, a phone or a document from a booking), Verificado (documento) (the desk scanned the
 *       document the record holds).</li>
 *   <li><b>Origen</b>: CRS (the hotel's own channels), Canal (an online agency), Touroperador — from its
 *       first booking's channel.</li>
 * </ul>
 *
 * <p>The values are the org's picklist values (restricted: anything else is refused).
 */
public record Marking(String state, Quality quality, Origin origin) {

    public static final String STATE_FIELD = "Estado_MDM__c";
    public static final String QUALITY_FIELD = "Calidad_Dato__c";
    public static final String ORIGIN_FIELD = "Origen__c";

    public static final String PROVISIONAL = "Provisional";
    public static final String CONSOLIDATED = "Consolidado";
    public static final String ANONYMIZED = "Anonimizado";

    public enum Quality {
        NAME_ONLY("Solo nombre"), WITH_CONTACT("Con contacto"), VERIFIED("Verificado (documento)");

        public final String label;

        Quality(String label) {
            this.label = label;
        }
    }

    public enum Origin {
        CRS("CRS"), CHANNEL("Canal"), TOUR_OPERATOR("Touroperador");

        public final String label;

        Origin(String label) {
            this.label = label;
        }

        /** The CRS channel a booking came in by: a tour operator's, an online agency's, or the hotel's own. */
        public static Origin ofChannel(String channelCode, String partnerCode) {
            if (channelCode == null || channelCode.isBlank()) {
                return null;
            }
            return switch (channelCode.trim().toUpperCase()) {
                case "TTOO", "TO", "TOUROPERADOR" -> TOUR_OPERATOR;
                case "OTA", "GDS" -> CHANNEL;
                default -> CRS;
            };
        }

        /** As kept on the customer; blank (tried, not told) and unknown names are null. */
        public static Origin named(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public static Marking of(Customer c) {
        var state = c.anonymizedAt != null ? ANONYMIZED
                : c.status == CustomerStatus.CONSOLIDATED ? CONSOLIDATED : PROVISIONAL;
        return new Marking(state, quality(c), Origin.named(c.origin));
    }

    /**
     * What there is to reach or identify the person by. A document counts as contact data — it
     * identifies — and is <em>verified</em> only when the desk scanned it: then the record holds the
     * person's own paper, not what somebody typed into a booking.
     */
    public static Quality quality(Customer c) {
        var hasDocument = present(c.documentNumber);
        if (hasDocument && c.documentVerifiedAt != null) {
            return Quality.VERIFIED;
        }
        if (present(c.email) || present(c.phone) || hasDocument) {
            return Quality.WITH_CONTACT;
        }
        return Quality.NAME_ONLY;
    }

    /** What its contact carries — the three fields, by their API names. */
    public Map<String, Object> fields() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put(STATE_FIELD, state);
        fields.put(QUALITY_FIELD, quality.label);
        fields.put(ORIGIN_FIELD, origin == null ? null : origin.label);
        return fields;
    }

    /** What is compared with the marking the contact has, to send it only when it changed. */
    public String key() {
        return state + "|" + quality.name() + "|" + (origin == null ? "" : origin.name());
    }

    /** For the Clientes card. */
    public String describe() {
        return quality.label + (origin == null ? "" : " · origen " + origin.label) + (ANONYMIZED.equals(state) ? " · anonimizado" : "");
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
