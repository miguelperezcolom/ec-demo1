package io.mateu.ecdemo1.pmsintegration;

import io.mateu.ecdemo1.integration.model.customer.CustomerChanged;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.integration.model.customer.GoldenRecord;
import io.mateu.ecdemo1.pmsintegration.frontoffice.FrontOfficeWriter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** What the MDM says about a customer becomes the front office's kardex. */
class KardexTest {

    static final GoldenRecord ANA = new GoldenRecord("Ana María", "García", "ana.maria@example.com", "+34 600 000 000",
            "ES", null, "DNI", "12345678Z");

    @Test
    void aDecidedChangeCarriesTheDataAndTheDecision() {
        var kardex = FrontOfficeWriter.kardexOf(new CustomerChanged("e", Instant.now(), "C-1", 2, ANA, true, "CR-1",
                "APPROVED", List.of("MRU01/ABC123")));
        assertThat(kardex).containsEntry("name", "Ana María García").containsEntry("email", "ana.maria@example.com")
                .containsEntry("phone", "+34 600 000 000").containsEntry("document", "12345678Z")
                .containsEntry("requestId", "CR-1").containsEntry("decision", "APPROVED");
    }

    @Test
    void aMergeCarriesTheSurvivorsDataAndNoDecision() {
        var kardex = FrontOfficeWriter.kardexOf(new CustomersMerged("e", Instant.now(), "C-1", 3, ANA, "C-2",
                List.of("MRU01/ABC123")));
        assertThat(kardex).containsEntry("name", "Ana María García").doesNotContainKey("decision");
    }
}
