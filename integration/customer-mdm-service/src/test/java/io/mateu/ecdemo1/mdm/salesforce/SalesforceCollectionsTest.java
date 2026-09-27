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
}
