package io.mateu.ecdemo1.mdm.salesforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.mdm.store.Customer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** sObject Collections' answer read record by record, in the order sent. */
class SalesforceCollectionsTest {

    static Customer customer(String id) {
        var c = new Customer();
        c.id = id;
        c.lastName = "X";
        return c;
    }

    @Test
    void eachRecordGetsItsContactOrWhySalesforceRefusedIt() throws Exception {
        var answer = new ObjectMapper().readTree("""
                [{"id":"003000000000001AAA","success":true,"created":true,"errors":[]},
                 {"success":false,"errors":[{"statusCode":"INVALID_EMAIL_ADDRESS","message":"Email: invalid","fields":["Email"]}]},
                 {"id":"003000000000003AAA","success":true,"created":false,"errors":[]}]""");

        var upserted = SalesforceClient.upserted(List.of(customer("C-1"), customer("C-2"), customer("C-3")), answer);

        assertThat(upserted).containsExactly(
                new SalesforceClient.Upserted("C-1", "003000000000001AAA", null),
                new SalesforceClient.Upserted("C-2", null, "INVALID_EMAIL_ADDRESS Email: invalid"),
                new SalesforceClient.Upserted("C-3", "003000000000003AAA", null));
    }

    @Test
    void aRecordWithoutAnAnswerIsNotTakenAsProjected() {
        var upserted = SalesforceClient.upserted(List.of(customer("C-1")), new ObjectMapper().createArrayNode());
        assertThat(upserted).singleElement().satisfies(u -> assertThat(u.ok()).isFalse());
    }

    @Test
    void theFieldsOfAContactAreTheCustomersWithALastNameAlways() {
        var c = customer("C-1");
        c.lastName = " ";
        c.birthDate = java.time.LocalDate.of(1984, 3, 2);
        assertThat(SalesforceClient.contactFields(c)).containsEntry("LastName", "?").containsEntry("Birthdate", "1984-03-02");
    }

    @Test
    void theContactListsEveryDocument_theMainOneFirst() {
        var c = customer("C-1");
        c.documentType = "DNI";
        c.documentNumber = "12345678-Z";
        c.documents = List.of(
                document("PASSPORT", "X1234567", "ESP", java.time.LocalDate.of(2031, 3, 12)),
                document("DNI", "12345678Z", "ESP", null));
        assertThat(SalesforceClient.contactFields(c)).containsEntry("Documentos__c",
                "DNI · ESP · 12345678Z · principal\nPasaporte · ESP · X1234567 · caduca 12/03/2031");
    }

    @Test
    void documentsNotRead_leaveTheContactsListAsItIs_noneEmptyIt() {
        var c = customer("C-1");
        assertThat(SalesforceClient.contactFields(c)).doesNotContainKey("Documentos__c");
        c.documents = List.of();
        assertThat(SalesforceClient.contactFields(c)).containsEntry("Documentos__c", null);
    }

    static io.mateu.ecdemo1.mdm.store.CustomerDocument document(String type, String number, String country,
                                                                java.time.LocalDate expiry) {
        var d = new io.mateu.ecdemo1.mdm.store.CustomerDocument();
        d.type = type;
        d.number = number;
        d.numberKey = io.mateu.ecdemo1.mdm.resolution.Normalizer.documentNumber(number);
        d.issuingCountry = country;
        d.expiry = expiry;
        return d;
    }

    @Test
    void anAnonymisedContactKeepsNothingPersonal_andSaysItIsAnonymised() {
        var c = customer("C-1");
        c.firstName = "Tomas";
        c.lastName = "Serra";
        c.nationality = "ES";
        c.anonymizedAt = java.time.Instant.now();
        assertThat(SalesforceClient.anonymousFields(c))
                .containsEntry("FirstName", null).containsEntry("LastName", "Anonimizado")
                .containsEntry("Email", null).containsEntry("Phone", null).containsEntry("Nationality__c", null)
                .containsEntry("Document_Number__c", null)
                .containsEntry("Estado_MDM__c", "Anonimizado").containsEntry("Calidad_Dato__c", "Solo nombre");
    }
}
