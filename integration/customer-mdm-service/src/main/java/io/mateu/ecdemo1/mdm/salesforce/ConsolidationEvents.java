package io.mateu.ecdemo1.mdm.salesforce;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.FetchRequest;
import com.salesforce.eventbus.protobuf.FetchResponse;
import com.salesforce.eventbus.protobuf.PubSubGrpc;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import com.salesforce.eventbus.protobuf.SchemaRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.mateu.ecdemo1.mdm.config.MdmProperties;
import io.mateu.ecdemo1.mdm.consolidation.Consolidations;
import io.mateu.ecdemo1.mdm.store.Cursor;
import io.mateu.ecdemo1.mdm.store.CursorRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The subscription to {@code ClienteConsolidado__e} through Salesforce's Pub/Sub API — the explicit
 * business event of the HLA's «Los SaaS entregan cambios de bajo nivel»: a flow in Salesforce says
 * "this customer left", instead of the MDM inferring merges from change data, which does not report
 * them.
 *
 * <p>Like the PMS's streaming in the other HLA: it resumes from the replay id of the last event it
 * handled, and deduplication is the inbox's ({@link Consolidations}). If Salesforce no longer has
 * that replay id (it keeps events three days), it starts from now and the poll covers the gap.
 */
@Component
@Slf4j
public class ConsolidationEvents implements SmartLifecycle {

    static final String TOPIC = "/event/ClienteConsolidado__e";
    static final String DECISIONS = "/event/CambioClienteResuelto__e";
    static final String CONTACT_CHANGES = "/event/ClienteActualizado__e";
    static final int BATCH = 25;

    /** A topic, where its replay position is kept, and what to do with each of its events. */
    record Topic(String name, String cursor, java.util.function.Consumer<GenericRecord> handler) {
    }

    final MdmProperties.Salesforce properties;
    final SalesforceClient salesforce;
    final Consolidations consolidations;
    final CursorRepository cursors;
    final Map<String, Schema> schemas = new ConcurrentHashMap<>();
    final java.util.List<Topic> topics;
    final java.util.List<Thread> threads = new java.util.concurrent.CopyOnWriteArrayList<>();

    volatile boolean running;
    volatile ManagedChannel channel;

    public ConsolidationEvents(MdmProperties properties, SalesforceClient salesforce, Consolidations consolidations,
                               CursorRepository cursors, io.mateu.ecdemo1.mdm.change.SalesforceInbox inbox) {
        this.properties = properties.salesforce();
        this.salesforce = salesforce;
        this.consolidations = consolidations;
        this.cursors = cursors;
        this.topics = java.util.List.of(
                // A contact left: merged into another, or deleted.
                new Topic(TOPIC, Cursor.PUBSUB, record -> consolidations.received(string(record, "AbsorbedMdmId__c"),
                        string(record, "AbsorbedContactId__c"), "EVENT")),
                // A change a hotel proposed was decided.
                new Topic(DECISIONS, "pubsub:CambioClienteResuelto__e", record -> inbox.decided(
                        string(record, "RequestId__c"), string(record, "Estado__c"), "EVENT")),
                // A contact's data changed in Salesforce, the master: the projection follows.
                new Topic(CONTACT_CHANGES, "pubsub:ClienteActualizado__e", record -> inbox.contactChanged(
                        string(record, "MdmId__c"))));
    }

    @Override
    public void start() {
        if (!salesforce.enabled() || !properties.subscribe()) {
            log.info("Not subscribing to Salesforce's events: Salesforce is not configured");
            return;
        }
        running = true;
        channel = ManagedChannelBuilder.forAddress(properties.pubsubHost(), properties.pubsubPort()).useTransportSecurity().build();
        for (var topic : topics) {
            threads.add(Thread.ofVirtual().name("salesforce-pubsub-" + topic.cursor()).start(() -> loop(topic)));
        }
    }

    @Override
    public void stop() {
        running = false;
        if (channel != null) {
            channel.shutdownNow();
        }
        threads.forEach(Thread::interrupt);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    void loop(Topic topic) {
        var backoff = 1_000L;
        while (running) {
            try {
                subscribeOnce(topic);
                backoff = 1_000L;
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                log.warn("Pub/Sub subscription to {} dropped: {}", topic.name(), e.getMessage());
            }
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                return;
            }
            backoff = Math.min(backoff * 2, 60_000L);
        }
    }

    /** One subscription, until the stream ends; then the loop opens the next one. */
    void subscribeOnce(Topic topic) throws InterruptedException {
        var session = salesforce.session();
        var headers = new Metadata();
        headers.put(Metadata.Key.of("accesstoken", Metadata.ASCII_STRING_MARSHALLER), session.accessToken());
        headers.put(Metadata.Key.of("instanceurl", Metadata.ASCII_STRING_MARSHALLER), session.instanceUrl());
        headers.put(Metadata.Key.of("tenantid", Metadata.ASCII_STRING_MARSHALLER), session.orgId());
        var interceptor = MetadataUtils.newAttachHeadersInterceptor(headers);
        var blocking = PubSubGrpc.newBlockingStub(channel).withInterceptors(interceptor);
        var async = PubSubGrpc.newStub(channel).withInterceptors(interceptor);

        var ended = new CountDownLatch(1);
        var failure = new Throwable[1];
        var requests = new StreamObserver[1];
        var responses = new StreamObserver<FetchResponse>() {
            @Override
            public void onNext(FetchResponse response) {
                for (var event : response.getEventsList()) {
                    try {
                        var record = decode(blocking, event.getEvent().getSchemaId(), event.getEvent().getPayload());
                        topic.handler().accept(record);
                    } catch (RuntimeException e) {
                        // The event is a hint, and the poll the net: one that cannot be handled now is
                        // left to the poll, instead of holding every event behind it.
                        log.warn("Event {} not handled, the poll will find it: {}", event.getEvent().getId(), e.getMessage());
                    }
                    remember(topic, event.getReplayId());
                }
                if (response.getEventsCount() == 0 && !response.getLatestReplayId().isEmpty()) {
                    // A keepalive: nothing happened up to here, so resuming here loses nothing.
                    remember(topic, response.getLatestReplayId());
                }
                if (response.getPendingNumRequested() == 0) {
                    requests[0].onNext(FetchRequest.newBuilder().setTopicName(topic.name()).setNumRequested(BATCH).build());
                }
            }

            @Override
            public void onError(Throwable t) {
                failure[0] = t;
                ended.countDown();
            }

            @Override
            public void onCompleted() {
                ended.countDown();
            }
        };
        requests[0] = async.subscribe(responses);
        var first = FetchRequest.newBuilder().setTopicName(topic.name()).setNumRequested(BATCH);
        var replayId = cursors.findById(topic.cursor()).map(c -> c.replayId).orElse(null);
        if (replayId != null) {
            first.setReplayPreset(ReplayPreset.CUSTOM).setReplayId(ByteString.copyFrom(Base64.getDecoder().decode(replayId)));
        } else {
            first.setReplayPreset(ReplayPreset.LATEST);
        }
        requests[0].onNext(first.build());
        log.info("Subscribed to {} from {}", topic.name(), replayId == null ? "now" : "the last event handled");
        while (running && !ended.await(1, TimeUnit.SECONDS)) {
            // waiting for the stream to end
        }
        if (failure[0] instanceof StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.UNAUTHENTICATED) {
                salesforce.expire();
            } else if (replayId != null && e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                // The replay id is gone from Salesforce: start from now; the poll finds what was missed.
                log.warn("Replay id no longer valid ({}); resuming from now", e.getStatus().getDescription());
                cursors.deleteById(topic.cursor());
            }
            throw e;
        }
    }

    GenericRecord decode(PubSubGrpc.PubSubBlockingStub blocking, String schemaId, ByteString payload) {
        var schema = schemas.computeIfAbsent(schemaId, id -> new Schema.Parser()
                .parse(blocking.getSchema(SchemaRequest.newBuilder().setSchemaId(id).build()).getSchemaJson()));
        try {
            return new GenericDatumReader<GenericRecord>(schema).read(null, DecoderFactory.get().binaryDecoder(payload.toByteArray(), null));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unreadable event of schema " + schemaId, e);
        }
    }

    void remember(Topic topic, ByteString replayId) {
        var cursor = cursors.findById(topic.cursor()).orElseGet(() -> {
            var fresh = new Cursor();
            fresh.name = topic.cursor();
            return fresh;
        });
        cursor.replayId = Base64.getEncoder().encodeToString(replayId.toByteArray());
        cursors.save(cursor);
    }

    static String string(GenericRecord record, String field) {
        var value = record.hasField(field) ? record.get(field) : null;
        return value == null ? null : value.toString();
    }
}
