package io.mateu.ecdemo1.integrations.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integrations.clients.Services;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The outbox of the commands this service sends other services over HTTP — the mapping, the master
 * of partners. A decision writes its commands here in its own transaction, so a decision rolled back
 * leaves none, and a decision saved leaves them to be sent: right after it commits, by
 * {@link #dispatchCommitted} on the same thread and outside any transaction, and — if the other
 * service does not answer — by {@link #relay} later, with backoff, until it does. No command is ever
 * sent while a database transaction waits on it.
 *
 * <p>A command can be sent twice (sent, and the other side's answer lost); every {@link RemoteCall}
 * is idempotent on the other side.
 */
@Component
@Slf4j
public class RemoteCalls {

    /** What the transactions committed on this thread asked to send, not sent yet. */
    static final ThreadLocal<List<Long>> COMMITTED = ThreadLocal.withInitial(ArrayList::new);

    static final Map<String, Class<? extends RemoteCall>> KINDS = Arrays.stream(RemoteCall.class.getPermittedSubclasses())
            .collect(Collectors.toMap(Class::getSimpleName, c -> c.asSubclass(RemoteCall.class)));

    final RemoteCallRepository repository;
    final Services services;
    final ObjectMapper objectMapper;
    final TransactionTemplate apart;
    final Clock clock;
    final int maxAttempts;
    final Duration claim = Duration.ofMinutes(2);

    public RemoteCalls(RemoteCallRepository repository, Services services, ObjectMapper objectMapper,
                       PlatformTransactionManager transactions, Clock clock,
                       @Value("${integrations.remote-calls.max-attempts:30}") int maxAttempts) {
        this.repository = repository;
        this.services = services;
        this.objectMapper = objectMapper;
        this.apart = new TransactionTemplate(transactions);
        this.apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    /** Asks for the call to be sent once — and only if — the current transaction commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(RemoteCall call) {
        var entity = new RemoteCallEntity();
        entity.kind = call.getClass().getSimpleName();
        entity.payload = serialise(call);
        entity.createdAt = clock.instant();
        entity.nextAttemptAt = entity.createdAt;
        repository.save(entity);
        var id = entity.id;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                COMMITTED.get().add(id);
            }
        });
    }

    /**
     * Sends what the transactions this thread committed asked for. Called once they are over — never
     * inside one. A call the other side does not take stays for {@link #relay}; this does not throw.
     */
    public void dispatchCommitted() {
        var ids = List.copyOf(COMMITTED.get());
        COMMITTED.remove();
        ids.forEach(this::dispatch);
    }

    /** Sends whatever is due: what could not be sent right after its commit, retried with backoff. */
    @Scheduled(fixedDelayString = "${integrations.remote-calls.interval:5s}")
    public void relay() {
        var due = apart.execute(status -> repository.due(clock.instant(), 100));
        if (due != null) {
            due.forEach(this::dispatch);
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    public void purgeDone() {
        apart.executeWithoutResult(status -> repository.deleteDoneBefore(clock.instant().minus(Duration.ofDays(7))));
    }

    /** Sends one call, if nobody else is sending it; outside any transaction but the bookkeeping's. */
    void dispatch(Long id) {
        var now = clock.instant();
        Integer claimed = apart.execute(status -> repository.claim(id, now, now.plus(claim)));
        if (claimed == null || claimed == 0) {
            return;
        }
        var entity = repository.findById(id).orElse(null);
        if (entity == null) {
            return;
        }
        RemoteCall call;
        try {
            call = deserialise(entity);
        } catch (RuntimeException e) {
            giveUp(id, "Unreadable: " + e.getMessage());
            return;
        }
        try {
            send(call);
        } catch (RuntimeException e) {
            failed(id, e);
            return;
        }
        apart.executeWithoutResult(status -> repository.findById(id).ifPresent(c -> {
            c.doneAt = clock.instant();
            c.attempts++;
            c.claimedUntil = null;
            c.lastError = null;
        }));
    }

    void send(RemoteCall call) {
        switch (call) {
            case RemoteCall.DefineHotel c -> services.defineHotel(c.crsHotelCode(), c.pmsHotelCode(), c.by());
            case RemoteCall.DefinePartnerTypes c -> services.definePartnerTypes(c.by());
            case RemoteCall.RequestAgentProposal c -> services.requestAgentProposal(c.crsHotelCode());
            case RemoteCall.ResyncPartner c -> services.resyncPartner(c.partnerCode());
            case RemoteCall.ResolveCause c -> services.resolveCauseIfOpen(c.causeKey(), c.by());
        }
    }

    void failed(Long id, RuntimeException e) {
        apart.executeWithoutResult(status -> repository.findById(id).ifPresent(c -> {
            c.attempts++;
            c.claimedUntil = null;
            c.lastError = abbreviate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            if (c.attempts >= maxAttempts) {
                c.failedAt = clock.instant();
                log.error("Remote call {} #{} given up after {} attempts: {}", c.kind, c.id, c.attempts, c.lastError);
            } else {
                c.nextAttemptAt = clock.instant().plus(backoff(c.attempts));
                log.warn("Remote call {} #{} failed (attempt {}), tried again at {}: {}", c.kind, c.id, c.attempts,
                        c.nextAttemptAt, c.lastError);
            }
        }));
    }

    void giveUp(Long id, String why) {
        apart.executeWithoutResult(status -> repository.findById(id).ifPresent(c -> {
            c.failedAt = clock.instant();
            c.claimedUntil = null;
            c.lastError = abbreviate(why);
            log.error("Remote call {} #{} given up: {}", c.kind, c.id, why);
        }));
    }

    /** 5 s, 10 s, 20 s… up to 10 minutes between attempts. */
    static Duration backoff(int attempts) {
        var seconds = 5L << Math.min(Math.max(attempts - 1, 0), 7);
        return Duration.ofSeconds(Math.min(seconds, 600));
    }

    static String abbreviate(String s) {
        return s.length() <= 2000 ? s : s.substring(0, 1997) + "...";
    }

    String serialise(RemoteCall call) {
        try {
            return objectMapper.writeValueAsString(call);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + call, e);
        }
    }

    RemoteCall deserialise(RemoteCallEntity entity) {
        var type = KINDS.get(entity.kind);
        if (type == null) {
            throw new IllegalStateException("No remote call of kind " + entity.kind);
        }
        try {
            return objectMapper.readValue(entity.payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** For the tests: what the outbox holds that is still to be sent, as its calls. */
    public List<RemoteCall> pending() {
        return apart.execute(status -> repository.findAll().stream().filter(c -> c.doneAt == null && c.failedAt == null)
                .map(this::deserialise).toList());
    }
}
