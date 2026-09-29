package io.mateu.ecdemo1.mdm.change;

import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A change request Salesforce refuses is said so and left: it does not hold back the ones behind it. */
class ChangeRequestsSendTest {

    @Test
    void aRefusedRequestDoesNotHoldBackTheNextOnesAndIsNotSentAgain() {
        var requests = mock(ChangeRequestRepository.class);
        var customers = mock(CustomerRepository.class);
        var salesforce = mock(SalesforceClient.class);
        var bad = request("CR-BAD");
        var good = request("CR-GOOD");
        when(requests.findByStatusOrderByRequestedAtAsc("PENDING")).thenReturn(List.of(bad, good));
        when(requests.findById("CR-BAD")).thenReturn(Optional.of(bad));
        when(requests.findById("CR-GOOD")).thenReturn(Optional.of(good));
        var customer = new Customer();
        customer.id = "C-1";
        customer.salesforceContactId = "003000000000001AAA";
        when(customers.findById("C-1")).thenReturn(Optional.of(customer));
        when(salesforce.available()).thenReturn(true);
        when(salesforce.upsertChangeCase(eq(bad), anyString(), anyString(), anyString())).thenThrow(
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                        "[{\"errorCode\":\"INVALID_EMAIL_ADDRESS\"}]".getBytes(), null));
        when(salesforce.upsertChangeCase(eq(good), anyString(), anyString(), anyString())).thenReturn("500000000000002AAA");
        var changeRequests = new ChangeRequests(requests, customers, salesforce, mock(SalesforceProjection.class),
                new TransactionTemplate(new NoTransactions()), Clock.fixed(Instant.parse("2026-11-12T09:30:00Z"), ZoneOffset.UTC));

        changeRequests.send();

        assertThat(good.salesforceCaseId).isEqualTo("500000000000002AAA");
        assertThat(bad.sendError).startsWith(ChangeRequests.REFUSED).contains("INVALID_EMAIL_ADDRESS");

        // Next tick: the refused one is not sent again.
        good.sentAt = Instant.now();
        changeRequests.send();
        verify(salesforce, org.mockito.Mockito.times(1)).upsertChangeCase(eq(bad), any(), any(), any());
    }

    static ChangeRequest request(String id) {
        var r = new ChangeRequest();
        r.id = id;
        r.customerId = "C-1";
        r.status = "PENDING";
        r.origin = "front office MRU01";
        r.changes = "email";
        return r;
    }

    static class NoTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
