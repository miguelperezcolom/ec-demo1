package io.mateu.ecdemo1.mdm.notice;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged.NoticeType;
import io.mateu.ecdemo1.mdm.outbox.Outbox;
import io.mateu.ecdemo1.mdm.salesforce.Backoff;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerNotice;
import io.mateu.ecdemo1.mdm.store.CustomerNotice.Sync;
import io.mateu.ecdemo1.mdm.store.CustomerNoticeRepository;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * A customer's reception notices. Salesforce is their master — a notice is a Case on the contact with
 * a «Tipo de aviso» — and the MDM keeps them as Salesforce last said, which is what it tells the
 * hotels (customer-notices). Salesforce tells the MDM of every notice created or changed there with
 * an event that carries it whole ({@code AvisoRecepcionCambiado__e}): nothing is read back.
 *
 * <p>The Clientes console asks for a notice, a change or a deactivation: it is kept apart as pending,
 * written to Salesforce with the others waiting — one call for all the new ones, one for the rest —
 * and it is the notice's only when its event comes back with it. While the daily API allowance is
 * spent, it waits. The event is the confirmation; a poll, only while a written one is unconfirmed, is
 * the net under a lost one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerNotices {

    /** How long a written notice may wait for its event before the poll asks for it. */
    static final Duration UNCONFIRMED = Duration.ofMinutes(2);

    final CustomerNoticeRepository notices;
    final CustomerRepository customers;
    final SalesforceClient salesforce;
    final Outbox outbox;
    final TransactionTemplate tx;
    final Clock clock;
    final ReentrantLock lock = new ReentrantLock();
    Backoff backoff;

    Backoff backoff() {
        if (backoff == null) {
            backoff = new Backoff(clock, Duration.ofSeconds(10), Duration.ofMinutes(10));
        }
        return backoff;
    }

    /** What the console asks a notice to be. */
    public record Draft(String text, NoticeType type, LocalDate from, LocalDate to, Set<NoticeMoment> showAt,
                        boolean active) {

        /** What Salesforce would refuse, or the desk could not use, said before anything is sent. */
        void check() {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("El aviso necesita un texto");
            }
            if (type == null) {
                throw new IllegalArgumentException("El aviso necesita un tipo");
            }
            if (showAt == null || showAt.isEmpty()) {
                throw new IllegalArgumentException("Indica dónde se muestra el aviso (check-in, check-out, estancia)");
            }
            if (from != null && to != null && to.isBefore(from)) {
                throw new IllegalArgumentException("La fecha «hasta» es anterior a «desde»");
            }
        }
    }

    // ── the console ─────────────────────────────────────────────────────────────

    /** A new notice for the customer, pending until Salesforce confirms it. */
    @Transactional
    public CustomerNotice create(String customerId, Draft draft, String by) {
        draft.check();
        var n = new CustomerNotice();
        n.id = "AV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        n.customerId = survivor(customerId).id;
        n.origin = "Clientes · " + by;
        ask(n, draft, by);
        log.info("{}: notice {} asked for by {} ({})", n.customerId, n.id, by, draft.type());
        return notices.save(n);
    }

    /** A change to a notice — its data, or active again — pending until Salesforce confirms it. */
    @Transactional
    public CustomerNotice change(String noticeId, Draft draft, String by) {
        draft.check();
        var n = get(noticeId);
        ask(n, draft, by);
        log.info("{}: change to notice {} asked for by {}", n.customerId, n.id, by);
        return notices.save(n);
    }

    /** The notice deactivated: the hotels stop showing it once Salesforce has it so. */
    @Transactional
    public CustomerNotice deactivate(String noticeId, String by) {
        var n = get(noticeId);
        var current = n.pending() && n.pendingText != null ? pendingDraft(n) : confirmedDraft(n);
        ask(n, new Draft(current.text(), current.type(), current.from(), current.to(), current.showAt(), false), by);
        log.info("{}: notice {} deactivated by {}", n.customerId, n.id, by);
        return notices.save(n);
    }

    void ask(CustomerNotice n, Draft d, String by) {
        n.pendingText = cut(d.text().trim(), 255);
        n.pendingType = d.type().name();
        n.pendingFrom = d.from();
        n.pendingTo = d.to();
        n.pendingShowAt = moments(d.showAt());
        n.pendingActive = d.active();
        n.sync = Sync.PENDING.name();
        n.requestedBy = by;
        n.requestedAt = clock.instant();
        n.sentAt = null;
        n.sendError = null;
    }

    public CustomerNotice get(String noticeId) {
        return notices.findById(noticeId).orElseThrow(() -> new NoSuchElementException("No hay ningún aviso " + noticeId));
    }

    /** The notices of a customer, under any of its codes, newest first. */
    public List<CustomerNotice> of(Collection<String> customerIds) {
        return notices.findByCustomerIdInOrderByRequestedAtDesc(customerIds);
    }

    // ── to Salesforce ───────────────────────────────────────────────────────────

    /**
     * Writes what the console asked for and Salesforce does not have — all at once, at most two calls —
     * once the customer is a contact there. Nothing while the allowance is spent.
     */
    @Scheduled(fixedDelayString = "${mdm.notice-tick:5s}")
    public void send() {
        if (!salesforce.available() || !backoff().ready()) {
            return;
        }
        var waiting = notices.findTop200BySyncOrderByRequestedAtAsc(Sync.PENDING.name());
        if (waiting.isEmpty()) {
            return;
        }
        var cases = new ArrayList<SalesforceClient.NoticeCase>();
        for (var n : waiting) {
            var contact = customers.findById(n.customerId).map(c -> c.salesforceContactId).orElse(null);
            if (contact == null && n.salesforceId == null) {
                continue; // the customer is not in Salesforce yet: its notice waits for it
            }
            cases.add(new SalesforceClient.NoticeCase(n.id, n.salesforceId, contact, n.pendingText,
                    salesforceType(n.pendingType), n.pendingFrom, n.pendingTo, salesforceMoments(n.pendingShowAt),
                    Boolean.TRUE.equals(n.pendingActive)));
        }
        if (cases.isEmpty()) {
            return;
        }
        List<SalesforceClient.Written> written;
        try {
            written = salesforce.writeNotices(cases);
            backoff().succeeded();
        } catch (SalesforceClient.LimitExceeded e) {
            return; // waits for the allowance, as it is
        } catch (RuntimeException e) {
            backoff().failed();
            log.warn("{} notice(s) not written to Salesforce yet, again at {}: {}", cases.size(), backoff().next(), e.getMessage());
            return;
        }
        for (var w : written) {
            tx.executeWithoutResult(s -> notices.findById(w.mdmNoticeId()).ifPresent(n -> {
                if (!Sync.PENDING.name().equals(n.sync)) {
                    return; // confirmed meanwhile: its event was quicker than this answer
                }
                if (w.ok()) {
                    n.salesforceId = w.caseId();
                    n.sync = Sync.SENT.name();
                    n.sentAt = clock.instant();
                } else {
                    n.sync = Sync.FAILED.name();
                    n.sendError = w.error();
                    log.warn("Salesforce refused notice {}: {}", n.id, w.error());
                }
                notices.save(n);
            }));
        }
    }

    /**
     * The net under a lost event: the notices written a while ago and still unconfirmed, asked for in
     * one query — only while there is any.
     */
    @Scheduled(fixedDelayString = "${mdm.notice-poll:5m}")
    public void poll() {
        if (!salesforce.available()) {
            return;
        }
        var cutoff = clock.instant().minus(UNCONFIRMED);
        var unconfirmed = notices.findTop200BySyncOrderByRequestedAtAsc(Sync.SENT.name()).stream()
                .filter(n -> n.salesforceId != null && n.sentAt != null && n.sentAt.isBefore(cutoff))
                .map(n -> n.salesforceId).toList();
        if (unconfirmed.isEmpty()) {
            return;
        }
        try {
            salesforce.noticeCases(unconfirmed).forEach(c -> received(fromCase(c)));
        } catch (SalesforceClient.LimitExceeded e) {
            log.debug("Asking Salesforce about notices waits for the allowance");
        } catch (RuntimeException e) {
            log.warn("Could not ask Salesforce about {} notice(s): {}", unconfirmed.size(), e.getMessage());
        }
    }

    // ── from Salesforce ─────────────────────────────────────────────────────────

    /**
     * Salesforce says how a notice is now: it becomes the MDM's, and the hotels are told if anything
     * changed. It confirms what the console asked when it carries it. One at a time: the event and the
     * poll may bring the same one together.
     */
    public void received(NoticeEvent e) {
        lock.lock();
        try {
            tx.executeWithoutResult(s -> apply(e));
        } finally {
            lock.unlock();
        }
    }

    void apply(NoticeEvent e) {
        if (e.mdmId() == null || e.mdmId().isBlank()) {
            log.debug("Notice {} is on a contact that is not an MDM customer: not taken", e.caseId());
            return;
        }
        var customer = customers.findById(e.mdmId()).orElse(null);
        while (customer != null && customer.aliasOf != null) {
            customer = customers.findById(customer.aliasOf).orElse(null);
        }
        if (customer == null) {
            log.debug("Notice {} is about {}, which this MDM does not have", e.caseId(), e.mdmId());
            return;
        }
        var n = (e.mdmNoticeId() == null ? null : notices.findById(e.mdmNoticeId()).orElse(null));
        if (n == null && e.caseId() != null) {
            n = notices.findFirstBySalesforceId(e.caseId()).orElse(null);
        }
        if (n == null) {
            n = new CustomerNotice();
            n.id = e.mdmNoticeId() != null && !e.mdmNoticeId().isBlank() ? e.mdmNoticeId()
                    : "AV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            n.origin = "Salesforce";
            n.sync = Sync.CONFIRMED.name();
            n.requestedAt = clock.instant();
        }
        var type = noticeType(e.type());
        var showAt = moments(mdmMoments(e.showAt()));
        var text = e.text() == null ? "" : cut(e.text().trim(), 255);
        var changed = n.version == 0 || !Objects.equals(n.customerId, customer.id) || !Objects.equals(n.text, text)
                || !Objects.equals(n.type, type) || !Objects.equals(n.fromDate, e.from()) || !Objects.equals(n.toDate, e.to())
                || !Objects.equals(n.showAt, showAt) || n.active != e.active();
        n.customerId = customer.id;
        n.salesforceId = e.caseId();
        n.text = text;
        n.type = type;
        n.fromDate = e.from();
        n.toDate = e.to();
        n.showAt = showAt;
        n.active = e.active();
        if (n.pending() && confirms(n)) {
            n.sync = Sync.CONFIRMED.name();
            n.sendError = null;
            log.info("{}: notice {} confirmed by Salesforce", n.customerId, n.id);
        }
        if (changed) {
            n.version++;
            n.confirmedAt = clock.instant();
            notices.save(n);
            outbox.appendNotice(event(n));
            log.info("{}: notice {} v{} ({}, {}) — told the hotels", n.customerId, n.id, n.version, n.type,
                    n.active ? "active" : "inactive");
        } else {
            notices.save(n);
        }
    }

    /** Whether Salesforce now has what the console asked for. */
    static boolean confirms(CustomerNotice n) {
        return n.pendingText == null || Objects.equals(n.text, n.pendingText) && Objects.equals(n.type, n.pendingType)
                && Objects.equals(n.fromDate, n.pendingFrom) && Objects.equals(n.toDate, n.pendingTo)
                && Objects.equals(n.showAt, n.pendingShowAt) && n.active == Boolean.TRUE.equals(n.pendingActive);
    }

    /** A customer absorbed by a merge: its notices are the survivor's now, and the hotels are told so. */
    @Transactional
    public void reassigned(String absorbedId, String survivorId) {
        for (var n : notices.findByCustomerId(absorbedId)) {
            n.customerId = survivorId;
            if (n.version > 0) {
                n.version++;
                n.confirmedAt = clock.instant();
                outbox.appendNotice(event(n));
            }
            notices.save(n);
        }
    }

    CustomerNoticeChanged event(CustomerNotice n) {
        return new CustomerNoticeChanged(UUID.randomUUID().toString(), clock.instant(), n.id, n.version, n.customerId,
                n.text, n.type == null ? null : NoticeType.valueOf(n.type), n.fromDate, n.toDate,
                n.showAt == null || n.showAt.isBlank() ? List.of()
                        : Arrays.stream(n.showAt.split(",")).map(NoticeMoment::valueOf).toList(),
                n.active, n.salesforceId);
    }

    /** A Case as the poll reads it, as its event would say it. */
    static NoticeEvent fromCase(JsonNode c) {
        var contact = c.path("Contact");
        return new NoticeEvent(text(c, "Id"), text(c, "MdmAvisoId__c"),
                contact.isMissingNode() || contact.isNull() ? null : text(contact, "MDM_Id__c"),
                text(c, "Subject"), text(c, "Aviso_Tipo__c"), date(text(c, "Aviso_Desde__c")),
                date(text(c, "Aviso_Hasta__c")), text(c, "Aviso_Mostrar_En__c"),
                c.path("Aviso_Activo__c").asBoolean(false) && !c.path("IsClosed").asBoolean(false)
                        && !c.path("IsDeleted").asBoolean(false));
    }

    // ── the words of each side ──────────────────────────────────────────────────

    static final java.util.Map<String, NoticeType> TYPES = java.util.Map.of(
            "Informativo", NoticeType.INFORMATIVE, "Importante", NoticeType.IMPORTANT, "Bloqueante", NoticeType.BLOCKING);
    static final java.util.Map<String, NoticeMoment> MOMENTS = java.util.Map.of(
            "Check-in", NoticeMoment.CHECK_IN, "Check-out", NoticeMoment.CHECK_OUT, "Estancia", NoticeMoment.STAY);

    /** Salesforce's picklist value as the MDM's type; an unknown one is taken as informative. */
    static String noticeType(String salesforce) {
        return (salesforce == null ? NoticeType.INFORMATIVE : TYPES.getOrDefault(salesforce.trim(), NoticeType.INFORMATIVE)).name();
    }

    static String salesforceType(String type) {
        var t = NoticeType.valueOf(type);
        return TYPES.entrySet().stream().filter(e -> e.getValue() == t).map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
    }

    static Set<NoticeMoment> mdmMoments(String salesforce) {
        var set = EnumSet.noneOf(NoticeMoment.class);
        if (salesforce != null) {
            Arrays.stream(salesforce.split(";")).map(String::trim).map(MOMENTS::get).filter(Objects::nonNull).forEach(set::add);
        }
        return set;
    }

    static String salesforceMoments(String moments) {
        var byMoment = new HashMap<NoticeMoment, String>();
        MOMENTS.forEach((k, v) -> byMoment.put(v, k));
        return moments == null || moments.isBlank() ? null : Arrays.stream(moments.split(","))
                .map(NoticeMoment::valueOf).sorted().map(byMoment::get).collect(Collectors.joining(";"));
    }

    /** In their declared order, comma separated: one spelling for one set. */
    static String moments(Set<NoticeMoment> moments) {
        return moments == null ? "" : moments.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    static Draft confirmedDraft(CustomerNotice n) {
        return new Draft(n.text, n.type == null ? NoticeType.INFORMATIVE : NoticeType.valueOf(n.type), n.fromDate, n.toDate,
                set(n.showAt), n.active);
    }

    static Draft pendingDraft(CustomerNotice n) {
        return new Draft(n.pendingText, NoticeType.valueOf(n.pendingType), n.pendingFrom, n.pendingTo, set(n.pendingShowAt),
                Boolean.TRUE.equals(n.pendingActive));
    }

    static Set<NoticeMoment> set(String moments) {
        var set = EnumSet.noneOf(NoticeMoment.class);
        if (moments != null && !moments.isBlank()) {
            Arrays.stream(moments.split(",")).map(NoticeMoment::valueOf).forEach(set::add);
        }
        return set;
    }

    Customer survivor(String id) {
        var c = customers.findById(id).orElseThrow(() -> new NoSuchElementException("No customer " + id));
        while (c.aliasOf != null) {
            c = customers.findById(c.aliasOf).orElseThrow();
        }
        return c;
    }

    static String text(JsonNode node, String field) {
        var v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    static LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    }

    static String cut(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }
}
