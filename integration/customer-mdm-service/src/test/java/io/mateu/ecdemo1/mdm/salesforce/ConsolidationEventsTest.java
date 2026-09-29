package io.mateu.ecdemo1.mdm.salesforce;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import io.mateu.ecdemo1.mdm.change.SalesforceInbox;
import io.mateu.ecdemo1.mdm.config.MdmProperties;
import io.mateu.ecdemo1.mdm.consolidation.Consolidations;
import io.mateu.ecdemo1.mdm.notice.CustomerNotices;
import io.mateu.ecdemo1.mdm.notice.NoticeEvent;
import io.mateu.ecdemo1.mdm.store.Cursor;
import io.mateu.ecdemo1.mdm.store.CursorRepository;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Pub/Sub subscription's own logic, without Salesforce: where each topic resumes from (its replay id,
 * kept in the database), and what each event does in the MDM.
 */
class ConsolidationEventsTest {

    final Map<String, Cursor> stored = new HashMap<>();
    final CursorRepository cursors = mock(CursorRepository.class);
    final Consolidations consolidations = mock(Consolidations.class);
    final SalesforceInbox inbox = mock(SalesforceInbox.class);
    final CustomerNotices notices = mock(CustomerNotices.class);
    ConsolidationEvents events;

    @BeforeEach
    void setUp() {
        when(cursors.findById(anyString())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
        when(cursors.save(any(Cursor.class))).thenAnswer(i -> {
            Cursor c = i.getArgument(0);
            stored.put(c.name, c);
            return c;
        });
        var properties = new MdmProperties(new MdmProperties.Salesforce("example.my.salesforce.com", "id", "secret", "v67.0",
                "api.pubsub.salesforce.com", 7443, true), null, null);
        events = new ConsolidationEvents(properties, mock(SalesforceClient.class), consolidations, cursors, inbox, notices,
                mock(ApplicationEventPublisher.class));
    }

    ConsolidationEvents.Topic topic(String name) {
        return events.topics.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void withoutAReplayIdASubscriptionStartsFromNow() {
        var first = ConsolidationEvents.firstRequest(ConsolidationEvents.TOPIC, null);
        assertThat(first.getReplayPreset()).isEqualTo(ReplayPreset.LATEST);
        assertThat(first.getTopicName()).isEqualTo("/event/ClienteConsolidado__e");
        assertThat(first.getNumRequested()).isEqualTo(ConsolidationEvents.BATCH);
    }

    @Test
    void theReplayIdOfTheLastEventHandledIsKept_andTheNextSubscriptionResumesFromIt() {
        var replayId = ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 1, 2, (byte) 0xFF});
        var topic = topic(ConsolidationEvents.DECISIONS);

        events.remember(topic, replayId);

        var kept = stored.get("pubsub:CambioClienteResuelto__e");
        assertThat(kept).isNotNull();
        // After a restart: read from the database, as subscribeOnce does.
        var first = ConsolidationEvents.firstRequest(topic.name(), kept.replayId);
        assertThat(first.getReplayPreset()).isEqualTo(ReplayPreset.CUSTOM);
        assertThat(first.getReplayId()).isEqualTo(replayId);
    }

    @Test
    void eachTopicKeepsItsOwnPosition_andALaterEventMovesIt() {
        events.remember(topic(ConsolidationEvents.TOPIC), ByteString.copyFrom(new byte[]{1}));
        events.remember(topic(ConsolidationEvents.NOTICES), ByteString.copyFrom(new byte[]{7}));
        events.remember(topic(ConsolidationEvents.TOPIC), ByteString.copyFrom(new byte[]{2}));

        assertThat(stored).containsOnlyKeys(Cursor.PUBSUB, "pubsub:AvisoRecepcionCambiado__e");
        assertThat(ConsolidationEvents.firstRequest("t", stored.get(Cursor.PUBSUB).replayId).getReplayId().byteAt(0)).isEqualTo((byte) 2);
    }

    static GenericRecord record(String name, String... fieldsAndValues) {
        var fields = new StringBuilder();
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            fields.append(i == 0 ? "" : ",").append("{\"name\":\"").append(fieldsAndValues[i])
                    .append("\",\"type\":[\"null\",\"string\"],\"default\":null}");
        }
        var schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[" + fields + "]}");
        var record = new GenericData.Record(schema);
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            record.put(fieldsAndValues[i], fieldsAndValues[i + 1]);
        }
        return record;
    }

    @Test
    void aMergeEventIsAConsolidation() {
        topic(ConsolidationEvents.TOPIC).handler().accept(record("ClienteConsolidado__e",
                "AbsorbedMdmId__c", "C-ABSORBED", "AbsorbedContactId__c", "003000000000001AAA"));
        verify(consolidations).received("C-ABSORBED", "003000000000001AAA", "EVENT");
    }

    @Test
    void aDecisionEventDecidesTheChangeRequest() {
        topic(ConsolidationEvents.DECISIONS).handler().accept(record("CambioClienteResuelto__e",
                "RequestId__c", "CR-1", "Estado__c", "Aprobada"));
        verify(inbox).decided("CR-1", "Aprobada", "EVENT");
    }

    @Test
    void aContactChangeEventRefreshesTheCustomer() {
        topic(ConsolidationEvents.CONTACT_CHANGES).handler().accept(record("ClienteActualizado__e", "MdmId__c", "C-1"));
        verify(inbox).contactChanged("C-1");
    }

    @Test
    void aNoticeEventCarriesTheWholeNotice() {
        var schema = new Schema.Parser().parse("""
                {"type":"record","name":"AvisoRecepcionCambiado__e","fields":[
                 {"name":"AvisoId__c","type":["null","string"]},{"name":"MdmAvisoId__c","type":["null","string"]},
                 {"name":"MdmId__c","type":["null","string"]},{"name":"Texto__c","type":["null","string"]},
                 {"name":"Tipo__c","type":["null","string"]},{"name":"Desde__c","type":["null","long"]},
                 {"name":"Hasta__c","type":["null","long"]},{"name":"MostrarEn__c","type":["null","string"]},
                 {"name":"Activo__c","type":["null","boolean"]}]}""");
        var record = new GenericData.Record(schema);
        record.put("AvisoId__c", "500000000000001AAA");
        record.put("MdmAvisoId__c", "N-1");
        record.put("MdmId__c", "C-1");
        record.put("Texto__c", "Alérgico al marisco");
        record.put("Tipo__c", "Importante");
        record.put("Desde__c", LocalDate.of(2026, 10, 1).toEpochDay());
        record.put("Hasta__c", LocalDate.of(2026, 10, 31).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli());
        record.put("MostrarEn__c", "Check-in;Estancia");
        record.put("Activo__c", true);

        topic(ConsolidationEvents.NOTICES).handler().accept(record);

        var captor = ArgumentCaptor.forClass(NoticeEvent.class);
        verify(notices).received(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new NoticeEvent("500000000000001AAA", "N-1", "C-1", "Alérgico al marisco",
                "Importante", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "Check-in;Estancia", true));
    }
}
