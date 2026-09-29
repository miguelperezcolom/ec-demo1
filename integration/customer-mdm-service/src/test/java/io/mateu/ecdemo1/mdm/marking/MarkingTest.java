package io.mateu.ecdemo1.mdm.marking;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClientFields;
import io.mateu.ecdemo1.mdm.store.Customer;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** How far a contact can be trusted, as the MDM marks it in Salesforce. */
class MarkingTest {

    static Customer named(String first, String last) {
        var c = new Customer();
        c.id = "C-1";
        c.status = CustomerStatus.PROVISIONAL;
        c.firstName = first;
        c.lastName = last;
        return c;
    }

    @Test
    void aNameAloneIsSoloNombre() {
        var c = named("Ana", "Gil");
        c.email = " ";
        assertThat(Marking.quality(c)).isEqualTo(Marking.Quality.NAME_ONLY);
    }

    @Test
    void anEmailAPhoneOrADocumentIsConContacto() {
        var byEmail = named("Ana", "Gil");
        byEmail.email = "ana@example.com";
        var byPhone = named("Ana", "Gil");
        byPhone.phone = "+34600000000";
        var byDocument = named("Ana", "Gil");
        byDocument.documentNumber = "12345678Z";
        assertThat(Marking.quality(byEmail)).isEqualTo(Marking.Quality.WITH_CONTACT);
        assertThat(Marking.quality(byPhone)).isEqualTo(Marking.Quality.WITH_CONTACT);
        assertThat(Marking.quality(byDocument)).isEqualTo(Marking.Quality.WITH_CONTACT);
    }

    @Test
    void aScannedDocumentIsVerificado_butOnlyWhileTheRecordHoldsADocument() {
        var c = named("Ana", "Gil");
        c.documentVerifiedAt = Instant.parse("2026-09-29T10:00:00Z");
        assertThat(Marking.quality(c)).isEqualTo(Marking.Quality.NAME_ONLY);
        c.documentNumber = "12345678Z";
        assertThat(Marking.quality(c)).isEqualTo(Marking.Quality.VERIFIED);
    }

    @Test
    void theStateFollowsTheGoldenRecord_andAnAnonymisedOneSaysSo() {
        var c = named("Ana", "Gil");
        assertThat(Marking.of(c).state()).isEqualTo("Provisional");
        c.status = CustomerStatus.CONSOLIDATED;
        assertThat(Marking.of(c).state()).isEqualTo("Consolidado");
        c.anonymizedAt = Instant.now();
        assertThat(Marking.of(c).state()).isEqualTo("Anonimizado");
    }

    @Test
    void theOriginIsTheChannelOfTheFirstBooking() {
        assertThat(Marking.Origin.ofChannel("TTOO", "04100343")).isEqualTo(Marking.Origin.TOUR_OPERATOR);
        assertThat(Marking.Origin.ofChannel("OTA", "09100035")).isEqualTo(Marking.Origin.CHANNEL);
        assertThat(Marking.Origin.ofChannel("WEB", null)).isEqualTo(Marking.Origin.CRS);
        assertThat(Marking.Origin.ofChannel("CALLCENTER", null)).isEqualTo(Marking.Origin.CRS);
        assertThat(Marking.Origin.ofChannel("WALKIN", null)).isEqualTo(Marking.Origin.CRS);
        assertThat(Marking.Origin.ofChannel(null, null)).isNull();
        assertThat(Marking.Origin.named("")).isNull();
        assertThat(Marking.Origin.named("CHANNEL")).isEqualTo(Marking.Origin.CHANNEL);
    }

    @Test
    void theContactCarriesTheOrgsPicklistValues() {
        var c = named("Ana", "Gil");
        c.phone = "600";
        c.origin = "TOUR_OPERATOR";
        assertThat(Marking.of(c).fields())
                .containsEntry("Estado_MDM__c", "Provisional")
                .containsEntry("Calidad_Dato__c", "Con contacto")
                .containsEntry("Origen__c", "Touroperador");
        assertThat(SalesforceClientFields.contact(c)).containsEntry("Calidad_Dato__c", "Con contacto");
    }

    @Test
    void aContactIsMarkedAgainOnlyWhenItsMarkingChanged() {
        var c = named("Ana", "Gil");
        assertThat(ContactMarking.needsMarking(c)).isTrue();
        c.markedAs = Marking.of(c).key();
        assertThat(ContactMarking.needsMarking(c)).isFalse();
        c.status = CustomerStatus.CONSOLIDATED;
        assertThat(ContactMarking.needsMarking(c)).isTrue();
        c.markedAs = Marking.of(c).key();
        c.origin = "";
        assertThat(ContactMarking.needsMarking(c)).as("an origin that cannot be told is no change").isFalse();
    }
}
