package io.mateu.ecdemo1.mdm.notice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged.NoticeType;
import io.mateu.ecdemo1.mdm.outbox.Outbox;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerNotice;
import io.mateu.ecdemo1.mdm.store.CustomerNoticeRepository;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The MDM's reception notices: what Salesforce says becomes the notice and is told to the hotels on
 * customer-notices (as its schema says); what the console asks for is written to Salesforce in one call
 * and is the notice's only once its event comes back; the allowance spent, it waits.
 */
class CustomerNoticesTest {

    static final Instant AT = Instant.parse("2026-11-12T09:30:00Z");

    final Map<String, CustomerNotice> stored = new LinkedHashMap<>();
    final Map<String, Customer> customerStore = new LinkedHashMap<>();
    final io.mateu.ecdemo1.messaging.Outbox shared = mock(io.mateu.ecdemo1.messaging.Outbox.class);
    final SalesforceClient salesforce = mock(SalesforceClient.class);
    CustomerNotices notices;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(AT, ZoneOffset.UTC);
        ObjectMapper boot;
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            boot = context.getBean(ObjectMapper.class);
        }
        customerStore.put("C-00042", customer("C-00042", "003000000000042AAA", null));
        customerStore.put("C-00051", customer("C-00051", null, "C-00042"));
        when(salesforce.available()).thenReturn(true);
        notices = new CustomerNotices(noticeRepository(), customerRepository(), salesforce, new Outbox(shared, boot, clock),
                new TransactionTemplate(new NoTransactions()), clock);
    }

    @Test
    void aNoticeCreatedInSalesforceIsTheMdmsAndTheHotelsAreToldAsTheSchemaSays() {
        notices.received(new NoticeEvent("500000000000001AAA", null, "C-00042", "Pedir el pasaporte original",
                "Bloqueante", LocalDate.of(2026, 11, 1), null, "Check-in;Estancia", true));

        var n = stored.values().iterator().next();
        assertThat(n.customerId).isEqualTo("C-00042");
        assertThat(n.version).isEqualTo(1);
        assertThat(n.type).isEqualTo("BLOCKING");
        assertThat(n.showAt).isEqualTo("CHECK_IN,STAY");
        assertThat(n.origin).isEqualTo("Salesforce");
        var payload = published(1).getFirst();
        Contracts.topic("customer-notices").assertValid(payload);
        assertThat(payload).contains("\"type\":\"BLOCKING\"").contains("\"showAt\":[\"CHECK_IN\",\"STAY\"]")
                .contains("\"customerId\":\"C-00042\"").contains("\"active\":true");
    }

    @Test
    void theSameNoticeSaidAgainChangesNothingAndIsNotToldTwice() {
        var event = new NoticeEvent("500000000000001AAA", null, "C-00042", "Alérgico", "Importante", null, null, "Check-in", true);
        notices.received(event);
        notices.received(event);

        assertThat(stored.values().iterator().next().version).isEqualTo(1);
        published(1);
    }

    @Test
    void aNoticeOfAContactThatIsNotACustomerIsNotTaken() {
        notices.received(new NoticeEvent("500000000000001AAA", null, null, "x", "Informativo", null, null, "Check-in", true));
        notices.received(new NoticeEvent("500000000000002AAA", null, "C-99999", "x", "Informativo", null, null, "Check-in", true));

        assertThat(stored).isEmpty();
        verify(shared, never()).append(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void whatTheConsoleAsksForIsPendingUntilSalesforceConfirmsIt() {
        var draft = new CustomerNotices.Draft("VIP: saludar por su nombre", NoticeType.BLOCKING, null, null,
                EnumSet.of(NoticeMoment.CHECK_IN, NoticeMoment.CHECK_OUT), true);
        // Asked for under a merged code: the survivor's.
        var asked = notices.create("C-00051", draft, "ana");
        assertThat(asked.customerId).isEqualTo("C-00042");
        assertThat(asked.sync).isEqualTo("PENDING");
        assertThat(asked.version).isZero();

        var sent = ArgumentCaptor.forClass(List.class);
        when(salesforce.writeNotices(sent.capture())).thenAnswer(i -> List.of(
                new SalesforceClient.Written(asked.id, "500000000000009AAA", null)));
        notices.send();

        @SuppressWarnings("unchecked")
        var cases = (List<SalesforceClient.NoticeCase>) sent.getValue();
        assertThat(cases).singleElement().satisfies(c -> {
            assertThat(c.caseId()).isNull();
            assertThat(c.contactId()).isEqualTo("003000000000042AAA");
            assertThat(c.type()).isEqualTo("Bloqueante");
            assertThat(c.showAt()).isEqualTo("Check-in;Check-out");
        });
        assertThat(stored.get(asked.id).sync).isEqualTo("SENT");
        assertThat(stored.get(asked.id).salesforceId).isEqualTo("500000000000009AAA");
        verify(shared, never()).append(anyString(), any(), anyString(), anyString(), any());

        // Its event comes back: confirmed, and only now the hotels are told.
        notices.received(new NoticeEvent("500000000000009AAA", asked.id, "C-00042", "VIP: saludar por su nombre",
                "Bloqueante", null, null, "Check-in;Check-out", true));
        var n = stored.get(asked.id);
        assertThat(n.sync).isEqualTo("CONFIRMED");
        assertThat(n.version).isEqualTo(1);
        assertThat(published(1).getFirst()).contains("\"noticeId\":\"" + asked.id + "\"");
    }

    @Test
    void aDeactivationIsWrittenByTheCasesIdAndTheHotelsLearnItOnceConfirmed() {
        notices.received(new NoticeEvent("500000000000001AAA", null, "C-00042", "Pago pendiente", "Importante", null, null,
                "Check-out", true));
        var id = stored.keySet().iterator().next();

        notices.deactivate(id, "ana");
        var sent = ArgumentCaptor.forClass(List.class);
        when(salesforce.writeNotices(sent.capture())).thenAnswer(i -> List.of(
                new SalesforceClient.Written(id, "500000000000001AAA", null)));
        notices.send();
        @SuppressWarnings("unchecked")
        var cases = (List<SalesforceClient.NoticeCase>) sent.getValue();
        assertThat(cases).singleElement().satisfies(c -> {
            assertThat(c.caseId()).isEqualTo("500000000000001AAA");
            assertThat(c.active()).isFalse();
            assertThat(c.text()).isEqualTo("Pago pendiente");
        });
        // Still active for the hotels until Salesforce says so.
        assertThat(stored.get(id).active).isTrue();

        notices.received(new NoticeEvent("500000000000001AAA", id, "C-00042", "Pago pendiente", "Importante", null, null,
                "Check-out", false));
        assertThat(stored.get(id).active).isFalse();
        assertThat(stored.get(id).sync).isEqualTo("CONFIRMED");
        assertThat(published(2).getLast()).contains("\"active\":false").contains("\"version\":2");
    }

    @Test
    void whileTheAllowanceIsSpentTheNoticeWaits() {
        var asked = notices.create("C-00042", new CustomerNotices.Draft("x", NoticeType.INFORMATIVE, null, null,
                EnumSet.of(NoticeMoment.CHECK_IN), true), "ana");
        when(salesforce.writeNotices(any())).thenThrow(new SalesforceClient.LimitExceeded("REQUEST_LIMIT_EXCEEDED"));

        notices.send();

        assertThat(stored.get(asked.id).sync).isEqualTo("PENDING");
        assertThat(stored.get(asked.id).sendError).isNull();
    }

    @Test
    void whatSalesforceRefusesSaysWhy() {
        var asked = notices.create("C-00042", new CustomerNotices.Draft("x", NoticeType.INFORMATIVE, null, null,
                EnumSet.of(NoticeMoment.CHECK_IN), true), "ana");
        when(salesforce.writeNotices(any())).thenReturn(List.of(
                new SalesforceClient.Written(asked.id, null, "FIELD_INTEGRITY_EXCEPTION bad value")));

        notices.send();

        assertThat(stored.get(asked.id).sync).isEqualTo("FAILED");
        assertThat(stored.get(asked.id).sendError).contains("FIELD_INTEGRITY_EXCEPTION");
    }

    @Test
    void aDraftWithoutTextTypeOrMomentIsRefusedBeforeAnythingIsSent() {
        assertThatThrownBy(() -> notices.create("C-00042", new CustomerNotices.Draft(" ", NoticeType.INFORMATIVE, null,
                null, EnumSet.of(NoticeMoment.CHECK_IN), true), "ana")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notices.create("C-00042", new CustomerNotices.Draft("x", NoticeType.INFORMATIVE, null,
                null, EnumSet.noneOf(NoticeMoment.class), true), "ana")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notices.create("C-00042", new CustomerNotices.Draft("x", NoticeType.INFORMATIVE,
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1), EnumSet.of(NoticeMoment.CHECK_IN), true), "ana"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stored).isEmpty();
    }

    @Test
    void aMergeGivesTheAbsorbedCustomersNoticesToTheSurvivor() {
        customerStore.put("C-00077", customer("C-00077", "003000000000077AAA", null));
        notices.received(new NoticeEvent("500000000000001AAA", null, "C-00077", "x", "Informativo", null, null, "Check-in", true));

        notices.reassigned("C-00077", "C-00042");

        var n = stored.values().iterator().next();
        assertThat(n.customerId).isEqualTo("C-00042");
        assertThat(n.version).isEqualTo(2);
        assertThat(published(2).getLast()).contains("\"customerId\":\"C-00042\"");
    }

    @Test
    void thePollReadsACaseAsItsEventWouldSayIt() throws Exception {
        var json = new ObjectMapper().readTree("""
                {"Id":"500000000000001AAA","IsDeleted":false,"IsClosed":true,"MdmAvisoId__c":"AV-1",
                 "Contact":{"MDM_Id__c":"C-00042"},"Subject":"Pago pendiente","Aviso_Tipo__c":"Importante",
                 "Aviso_Desde__c":"2026-11-01","Aviso_Hasta__c":null,"Aviso_Mostrar_En__c":"Check-out","Aviso_Activo__c":true}""");

        var e = CustomerNotices.fromCase(json);

        assertThat(e.mdmId()).isEqualTo("C-00042");
        assertThat(e.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(e.active()).as("a closed Case is an inactive notice").isFalse();
    }

    List<String> published(int count) {
        var payloads = ArgumentCaptor.forClass(String.class);
        verify(shared, times(count)).append(eq(Outbox.CUSTOMER_NOTICES), anyString(), eq("CustomerNoticeChanged"),
                payloads.capture(), any());
        payloads.getAllValues().forEach(Contracts.topic("customer-notices")::assertValid);
        return payloads.getAllValues();
    }

    static Customer customer(String id, String contact, String aliasOf) {
        var c = new Customer();
        c.id = id;
        c.salesforceContactId = contact;
        c.aliasOf = aliasOf;
        return c;
    }

    CustomerNoticeRepository noticeRepository() {
        return (CustomerNoticeRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CustomerNoticeRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.ofNullable(stored.get((String) args[0]));
                    case "save" -> {
                        var n = (CustomerNotice) args[0];
                        stored.put(n.id, n);
                        yield n;
                    }
                    case "findFirstBySalesforceId" -> stored.values().stream()
                            .filter(n -> args[0].equals(n.salesforceId)).findFirst();
                    case "findByCustomerId" -> stored.values().stream().filter(n -> args[0].equals(n.customerId)).toList();
                    case "findByCustomerIdInOrderByRequestedAtDesc" -> stored.values().stream()
                            .filter(n -> ((Collection<?>) args[0]).contains(n.customerId)).toList();
                    case "findTop200BySyncOrderByRequestedAtAsc" -> stored.values().stream()
                            .filter(n -> args[0].equals(n.sync))
                            .sorted(Comparator.comparing(n -> n.requestedAt)).toList();
                    case "toString" -> "notices";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    CustomerRepository customerRepository() {
        return (CustomerRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CustomerRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.ofNullable(customerStore.get((String) args[0]));
                    case "toString" -> "customers";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
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
