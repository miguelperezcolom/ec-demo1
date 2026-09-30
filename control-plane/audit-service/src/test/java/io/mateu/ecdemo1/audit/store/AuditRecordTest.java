package io.mateu.ecdemo1.audit.store;

import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** What a reservation's history is looked up by: the stay and the locator, read from the action's parameters. */
class AuditRecordTest {

    static AuditedAction action(String parameters) {
        return new AuditedAction("a1", Instant.EPOCH, "front-office", "Room change", "MRU01", "ana", parameters, true, "OK");
    }

    @Test
    void theStayAndTheLocatorComeFromTheParameters() {
        var r = AuditRecord.of(action("{\"stayId\":\"FO-1\",\"locator\":\"97R5DW\",\"to\":\"202\"}"), Instant.EPOCH);
        assertThat(r.stayId).isEqualTo("FO-1");
        assertThat(r.locator).isEqualTo("97R5DW");
        assertThat(r.actor).isEqualTo("ana");
    }

    @Test
    void anActionOnNoReservationHasNeither() {
        var r = AuditRecord.of(action("{\"integration\":\"MRU01\"}"), Instant.EPOCH);
        assertThat(r.stayId).isNull();
        assertThat(r.locator).isNull();
        assertThat(AuditRecord.of(action("not json"), Instant.EPOCH).stayId).isNull();
        assertThat(AuditRecord.of(action(null), Instant.EPOCH).locator).isNull();
    }
}
