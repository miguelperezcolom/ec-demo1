package io.mateu.ecdemo1.crsintegration.commands;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What this adapter asks of the systems it fronts — the CRS (booking) and the master of partners
 * (the ERP) — without waiting for an answer, in each system's own terms: its commands topic, which it
 * consumes once per {@code commandId}. Written to the outbox, never sent over HTTP.
 */
public final class SystemCommands {

    private SystemCommands() {
    }

    /** For the CRS ({@code booking-commands}): where the booking landed in the PMS. */
    public record AnnotatePmsReference(String commandId, String bookingId, String pmsReservationId) {
        @JsonProperty("type")
        public String type() {
            return "annotate-pms-reference";
        }

        @JsonIgnore
        public String key() {
            return bookingId;
        }
    }

    /** For the master of partners ({@code partner-commands}): which profile the partner is in the PMS. */
    public record RecordPmsProfile(String commandId, String partnerCode, String profileId, String profileType) {
        @JsonProperty("type")
        public String type() {
            return "record-pms-profile";
        }

        @JsonIgnore
        public String key() {
            return partnerCode;
        }
    }
}
