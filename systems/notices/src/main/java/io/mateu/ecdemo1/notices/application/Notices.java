package io.mateu.ecdemo1.notices.application;

import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeType;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.SubjectType;
import io.mateu.ecdemo1.notices.store.Notice;
import io.mateu.ecdemo1.notices.store.NoticeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * The reception notices: a reservation's and a partner's, kept here and managed from the data plane's
 * console (or by its agent), and the customers', taken as Salesforce — their master — has them, through
 * the MDM (customer-notices), and never edited here. Every change, of either kind, is published whole
 * on {@code notices}, where the front office keeps its copy of them.
 */
@Service
@Slf4j
public class Notices {

    /** The inbox's consumer name for customer-notices. */
    public static final String CUSTOMER_NOTICES = "customer-notices";

    /** What a person (or the agent) asks for: a notice of a reservation or a partner. */
    public record Draft(SubjectType subjectType, String subjectId, String hotelCode, String text, NoticeType type,
                        LocalDate from, LocalDate to, Set<NoticeMoment> moments, boolean active) {
    }

    /** Where every change goes: the outbox, and from it the notices topic. */
    public interface Events {
        void publish(NoticeChanged event);
    }

    /** The master of partners (the ERP): a partner's name, by its code; empty if it has none. */
    public interface Partners {
        java.util.Optional<String> name(String code);
    }

    /** Where a consumer deduplicates what it receives (at-least-once delivery). */
    public interface Inbox {
        boolean firstTime(String consumer, String messageId);
    }

    final NoticeRepository notices;
    final Events events;
    final Partners partners;
    final Inbox inbox;
    final Clock clock;

    public Notices(NoticeRepository notices, Events events, Partners partners, Inbox inbox, Clock clock) {
        this.notices = notices;
        this.events = events;
        this.partners = partners;
        this.inbox = inbox;
        this.clock = clock;
    }

    // ── the ones kept here ──────────────────────────────────────────────────────

    @Transactional
    public Notice create(Draft draft, String by) {
        var n = new Notice();
        n.id = "AV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        n.subjectType = subjectOf(draft).name();
        n.subjectId = subjectIdOf(draft);
        n.subjectName = draft.subjectType() == SubjectType.PARTNER ? partnerName(n.subjectId) : null;
        n.source = Notice.NOTICES;
        n.createdAt = clock.instant();
        n.version = 0;
        apply(n, draft, by);
        log.info("Notice {} created on {} {} by {}", n.id, n.subjectType, n.subjectId, by);
        return n;
    }

    /** Changes what a notice says and when; its subject stays. A customer's is Salesforce's: refused. */
    @Transactional
    public Notice update(String id, Draft draft, String by) {
        var n = ours(id);
        apply(n, draft, by);
        log.info("Notice {} v{} changed by {}", n.id, n.version, by);
        return n;
    }

    @Transactional
    public Notice setActive(String id, boolean active, String by) {
        var n = ours(id);
        n.active = active;
        touch(n, by);
        log.info("Notice {} {} by {}", id, active ? "activated" : "deactivated", by);
        return n;
    }

    void apply(Notice n, Draft d, String by) {
        if (d.text() == null || d.text().isBlank()) {
            throw new IllegalArgumentException("El aviso necesita un texto");
        }
        if (d.text().length() > 300) {
            throw new IllegalArgumentException("El texto del aviso no puede pasar de 300 caracteres");
        }
        if (d.moments() == null || d.moments().isEmpty()) {
            throw new IllegalArgumentException("Indica en qué momento lo ve recepción: antes de la llegada, "
                    + "en el check-in, durante la estancia o en el check-out");
        }
        if (d.from() != null && d.to() != null && d.to().isBefore(d.from())) {
            throw new IllegalArgumentException("La fecha hasta es anterior a la fecha desde");
        }
        n.hotelCode = d.hotelCode() == null || d.hotelCode().isBlank() ? null : d.hotelCode().trim().toUpperCase(Locale.ROOT);
        n.text = d.text().trim();
        n.type = (d.type() == null ? NoticeType.INFORMATIVE : d.type()).name();
        n.fromDate = d.from();
        n.toDate = d.to();
        n.moments = Notice.moments(d.moments());
        n.active = d.active();
        touch(n, by);
    }

    void touch(Notice n, String by) {
        n.version++;
        n.updatedAt = clock.instant();
        n.updatedBy = by;
        notices.save(n);
        events.publish(event(n));
    }

    Notice ours(String id) {
        var n = find(id);
        if (n.salesforce()) {
            throw new IllegalStateException("Los avisos de cliente se gestionan en Salesforce, su maestro: "
                    + "aquí solo se leen");
        }
        return n;
    }

    public Notice find(String id) {
        return notices.findById(id).orElseThrow(() -> new NoSuchElementException("No existe el aviso " + id));
    }

    static SubjectType subjectOf(Draft d) {
        if (d.subjectType() == null) {
            throw new IllegalArgumentException("Indica de qué es el aviso: de una reserva o de una agencia");
        }
        if (d.subjectType() == SubjectType.CUSTOMER) {
            throw new IllegalArgumentException("Los avisos de cliente se crean en Salesforce, su maestro");
        }
        return d.subjectType();
    }

    static String subjectIdOf(Draft d) {
        if (d.subjectId() == null || d.subjectId().isBlank()) {
            throw new IllegalArgumentException(d.subjectType() == SubjectType.PARTNER
                    ? "Indica el código de la agencia" : "Indica el localizador de la reserva");
        }
        return d.subjectId().trim().toUpperCase(Locale.ROOT);
    }

    String partnerName(String code) {
        return partners.name(code).orElseThrow(() ->
                new IllegalArgumentException("El ERP no tiene ninguna agencia con el código " + code));
    }

    // ── the customers', from Salesforce ─────────────────────────────────────────

    /**
     * A customer's notice as the MDM sends it: kept (and published on notices) unless the one kept is
     * this version or a newer one. Once per event. {@code STAY}, its moment for the stay, is IN_HOUSE.
     */
    @Transactional
    public boolean take(CustomerNoticeChanged e) {
        if (e.eventId() != null && !inbox.firstTime(CUSTOMER_NOTICES, e.eventId())) {
            return false;
        }
        var kept = notices.findById(e.noticeId()).orElse(null);
        if (kept != null && kept.version >= e.version()) {
            return false;
        }
        var n = kept == null ? new Notice() : kept;
        if (kept == null) {
            n.id = e.noticeId();
            n.createdAt = e.occurredAt() == null ? clock.instant() : e.occurredAt();
        }
        n.subjectType = SubjectType.CUSTOMER.name();
        n.subjectId = e.customerId();
        n.subjectName = null;
        n.hotelCode = null;
        n.text = e.text();
        n.type = e.type() == null ? NoticeType.INFORMATIVE.name() : e.type().name();
        n.fromDate = e.from();
        n.toDate = e.to();
        var moments = EnumSet.noneOf(NoticeMoment.class);
        if (e.showAt() != null) {
            e.showAt().forEach(m -> {
                var moment = Notice.moment(m.name());
                if (moment != null) {
                    moments.add(moment);
                }
            });
        }
        n.moments = Notice.moments(moments);
        n.active = e.active();
        n.version = e.version();
        n.source = Notice.SALESFORCE;
        n.sourceRef = e.salesforceId();
        n.updatedAt = e.occurredAt() == null ? clock.instant() : e.occurredAt();
        n.updatedBy = "Salesforce";
        notices.save(n);
        events.publish(event(n));
        log.info("{}: customer notice {} v{} ({})", e.customerId(), e.noticeId(), e.version(),
                e.active() ? "active" : "inactive");
        return true;
    }

    // ── what applies ────────────────────────────────────────────────────────────

    /**
     * The notices the desk sees of that subject, at that hotel and moment, some day from
     * {@code arrival} to {@code departure} — the most serious first.
     */
    public List<Notice> applicable(SubjectType subject, String subjectId, String hotel, NoticeMoment moment,
                                   LocalDate arrival, LocalDate departure) {
        var result = new ArrayList<Notice>();
        var id = subjectId == null ? "" : subject == SubjectType.CUSTOMER ? subjectId.trim()
                : subjectId.trim().toUpperCase(Locale.ROOT);
        for (var n : notices.findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(subject.name(), id)) {
            if (n.appliesTo(hotel, moment, arrival, departure)) {
                result.add(n);
            }
        }
        result.sort(Comparator.comparing((Notice n) -> n.noticeType().ordinal()).reversed());
        return result;
    }

    /**
     * Every notice published again, as it is — for a reader that lost its copy (a new front office's
     * database). A reader that has them keeps what it has: the versions are the same.
     */
    @Transactional
    public int republishAll() {
        var all = notices.findAll();
        all.forEach(n -> events.publish(event(n)));
        log.info("{} notice(s) published again", all.size());
        return all.size();
    }

    public NoticeChanged event(Notice n) {
        return new NoticeChanged(UUID.randomUUID().toString(), clock.instant(), n.id, n.version, n.subject(),
                n.subjectId, n.subjectName, n.hotelCode, n.text, n.noticeType(), n.fromDate, n.toDate,
                n.momentSet().stream().sorted().toList(), n.active, n.source, n.sourceRef);
    }
}
