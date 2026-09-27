package io.mateu.ecdemo1.integration.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A hotel says nobody of a reservation arrived (HLA F006): the CRS adapter starts «Registrar no-show»,
 * and the CRS decides what it costs. Sent through the front office's outbox on the
 * {@code no-show-reports} topic. Taken twice, the process starts once. What the CRS decides comes back
 * down the chain as a cancellation; one it cannot take (a reservation not in the CRS, one already
 * cancelled) is logged and dropped.
 */
public record ReportNoShow(String commandId, String hotelCode, String locator, String reportedBy) {

    /** The Kafka key: the reports about one reservation stay in order. */
    @JsonIgnore
    public String key() {
        return hotelCode + "/" + locator;
    }
}
