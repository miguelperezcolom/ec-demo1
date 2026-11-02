package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.AcknowledgementRepository;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.Mailer;
import io.mateu.ecdemo1.contenthub.application.out.NotificationLog;
import io.mateu.ecdemo1.contenthub.application.out.NotificationLog.Channel;
import io.mateu.ecdemo1.contenthub.application.out.NotificationLog.Kind;
import io.mateu.ecdemo1.contenthub.application.out.NotificationPreferences;
import io.mateu.ecdemo1.contenthub.application.out.NotificationSender;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.ack.Acknowledgement;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Telling readers about what the intranet publishes, off the request's thread, however many they are.
 *
 * <p>An item that notifies and has become effective — published now, or its publication date crossed —
 * gets one job per version. The job asks THE visibility function once per profile and language who can
 * see it, and tells them in batches: in each batch's transaction a row per reader says it was done and
 * the notification goes into the outbox, so a job cut short carries on where it was and nobody hears
 * twice.
 *
 * <p>It looks at the dates rather than at the events that cross them: a crossing that happened while the
 * service was down is still found, within {@code catch-up}. What became effective before that is not
 * news, which is also why turning this on does not announce everything already published.
 *
 * <p>An open notification closes when the reader opens the item (a reminder, when they confirm it), when
 * the item stops being readable, or after {@code close-after}: the inbox is for what is still waiting.
 */
@Service
@Slf4j
public class ItemNotifications {

    static final String LINK = "/intranet/noticias/";
    static final int JOBS_PER_PASS = 20;
    static final int MAX_ATTEMPTS = 20;
    static final int CLOSE_BATCH = 500;
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Europe/Madrid"));

    final ItemRepository items;
    final EmployeeDirectory directory;
    final Visibility visibility;
    final ContentIndex index;
    final AcknowledgementRepository acks;
    final NotificationLog notifications;
    final NotificationPreferences preferences;
    final NotificationSender sender;
    final Mailer mailer;
    final TransactionOperations transactions;
    final Clock clock;
    final Duration catchUp;
    final Duration reminderBefore;
    final Duration closeAfter;
    final int batchSize;
    final String portalUrl;

    public ItemNotifications(ItemRepository items, EmployeeDirectory directory, Visibility visibility, ContentIndex index,
                             AcknowledgementRepository acks, NotificationLog notifications,
                             NotificationPreferences preferences, NotificationSender sender, Mailer mailer,
                             TransactionOperations transactions, Clock clock,
                             @Value("${content-hub.notifications.catch-up:PT24H}") Duration catchUp,
                             @Value("${content-hub.notifications.reminder-before:P2D}") Duration reminderBefore,
                             @Value("${content-hub.notifications.close-after:P30D}") Duration closeAfter,
                             @Value("${content-hub.notifications.batch-size:200}") int batchSize,
                             @Value("${content-hub.portal-url:http://localhost:8800/intranet}") String portalUrl) {
        this.items = items;
        this.directory = directory;
        this.visibility = visibility;
        this.index = index;
        this.acks = acks;
        this.notifications = notifications;
        this.preferences = preferences;
        this.sender = sender;
        this.mailer = mailer;
        this.transactions = transactions;
        this.clock = clock;
        this.catchUp = catchUp;
        this.reminderBefore = reminderBefore;
        this.closeAfter = closeAfter;
        this.batchSize = Math.max(1, batchSize);
        this.portalUrl = portalUrl;
    }

    /** One pass: what has just become effective gets its job, and the pending jobs are done. */
    public void fanOut() {
        var now = clock.instant();
        for (var item : items.findAll()) {
            item.publishedVersion().filter(v -> dueNow(item, v, now))
                    .ifPresent(v -> notifications.enqueue(item.id(), v.number(), now));
        }
        for (var job : notifications.pendingJobs(JOBS_PER_PASS)) {
            try {
                run(job);
            } catch (RuntimeException e) {
                // what was committed stays; the next pass carries on from there
                log.warn("Notifying readers of {} v{} failed: {}", job.itemId(), job.version(), e.getMessage());
                giveUpOrRetry(job, e.getMessage());
            }
        }
    }

    boolean dueNow(Item item, ItemVersion version, Instant now) {
        var content = version.content();
        if (!content.notifies(item.type())) {
            return false;
        }
        var effective = effectiveAt(version);
        return !effective.isAfter(now) && effective.isAfter(now.minus(catchUp))
                && (content.expireAt() == null || content.expireAt().isAfter(now));
    }

    /** Readers see a version from its publication and its date, whichever comes later. */
    static Instant effectiveAt(ItemVersion version) {
        var publishAt = version.content().publishAt();
        return publishAt != null && publishAt.isAfter(version.publishedAt()) ? publishAt : version.publishedAt();
    }

    void run(NotificationLog.Job job) {
        var now = clock.instant();
        var item = items.find(job.itemId()).orElse(null);
        var version = item == null ? Optional.<ItemVersion>empty() : item.publishedVersion();
        if (version.isEmpty() || version.get().number() != job.version()) {
            // retired, or a newer version has taken over and has its own job
            notifications.finish(job, "Ya no se publica esta versión", now);
            return;
        }
        if (!applied(item, job.version())) {
            return; // the projection is applied after the commit: until then nobody "can see" it
        }
        var delivery = deliver(item, Kind.PUBLISHED, job.version(), version.get().content().important(), Set.of());
        if (delivery.mailError() != null) {
            giveUpOrRetry(job, delivery.mailError());
            return;
        }
        notifications.finish(job, delivery.inApp() + " en la aplicación, " + delivery.emails() + " por correo", clock.instant());
        log.info("Item {} v{}: {} reader(s) told in the app, {} by e-mail", item.id(), job.version(), delivery.inApp(),
                delivery.emails());
    }

    void giveUpOrRetry(NotificationLog.Job job, String error) {
        if (job.attempts() + 1 >= MAX_ATTEMPTS) {
            notifications.finish(job, "Abandonado: " + error, clock.instant());
        } else {
            notifications.attempted(job);
        }
    }

    boolean applied(Item item, int version) {
        return index.applied(item.id()).filter(a -> a.version() >= version).isPresent();
    }

    /**
     * The reminder: a reader who can see an item they must confirm, has not, and whose deadline is near,
     * is told once per confirmation version. Whether the item notified when published is another matter:
     * this is something they have to do.
     */
    public void remindDue() {
        var now = clock.instant();
        for (var item : items.findAll()) {
            var version = item.publishedVersion().orElse(null);
            if (version == null) {
                continue;
            }
            var content = version.content();
            var deadline = content.confirmationDeadline();
            if (!content.confirmationRequired() || deadline == null || !deadline.isAfter(now)
                    || deadline.isAfter(now.plus(reminderBefore)) || !applied(item, version.number())) {
                continue;
            }
            var counted = Math.max(1, item.confirmationVersion());
            try {
                var confirmed = acks.users(item.id(), Acknowledgement.Type.CONFIRMATION, counted);
                var delivery = deliver(item, Kind.CONFIRMATION_DUE, counted, content.important(), confirmed);
                if (delivery.inApp() + delivery.emails() > 0) {
                    log.info("Item {}: {} reader(s) reminded to confirm, {} by e-mail", item.id(), delivery.inApp(),
                            delivery.emails());
                }
            } catch (RuntimeException e) {
                log.warn("Reminding readers of {} failed: {}", item.id(), e.getMessage());
            }
        }
    }

    record Delivery(int inApp, int emails, String mailError) {
    }

    record Addressee(Employee employee, VisibleItem item) {
    }

    /** Tells who can see the item and has not been told, as each of them wants, but for those in {@code except}. */
    Delivery deliver(Item item, Kind kind, int version, boolean byEmail, Set<String> except) {
        var toldInApp = notifications.sentTo(item.id(), version, kind, Channel.IN_APP);
        var toldByEmail = byEmail ? notifications.sentTo(item.id(), version, kind, Channel.EMAIL) : Set.<String>of();
        var wishes = preferences.all();
        var seen = new HashMap<String, Optional<VisibleItem>>();
        var inApp = new ArrayList<Addressee>();
        var emails = new LinkedHashMap<String, List<Addressee>>();
        for (var employee : directory.all()) {
            if (!employee.enabled() || except.contains(employee.userId())) {
                continue;
            }
            // Asked per profile and language: the answer is the same for all who share them.
            var visible = seen.computeIfAbsent(employee.hash() + "|" + employee.attributes().language(),
                    k -> visibility.item(employee.attributes(), item.id()));
            if (visible.isEmpty()) {
                continue;
            }
            var wish = wishes.getOrDefault(employee.userId(), NotificationPreferences.Preference.DEFAULT);
            var addressee = new Addressee(employee, visible.get());
            if (wish.inApp() && !toldInApp.contains(employee.userId())) {
                inApp.add(addressee);
            }
            if (byEmail && wish.email() && hasEmail(employee) && !toldByEmail.contains(employee.userId())) {
                emails.computeIfAbsent(employee.attributes().language(), k -> new ArrayList<>()).add(addressee);
            }
        }
        var toldNow = 0;
        for (var batch : batches(inApp)) {
            toldNow += sendBatch(batch, kind, version);
        }
        var mailed = 0;
        String mailError = null;
        for (var byLanguage : emails.values()) {
            for (var batch : batches(byLanguage)) {
                try {
                    mailed += mailBatch(batch, kind, version);
                } catch (RuntimeException e) {
                    log.warn("E-mail about {} not sent: {}", item.id(), e.getMessage());
                    mailError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                }
            }
        }
        return new Delivery(toldNow, mailed, mailError);
    }

    static boolean hasEmail(Employee employee) {
        return employee.email() != null && !employee.email().isBlank();
    }

    List<List<Addressee>> batches(List<Addressee> all) {
        var out = new ArrayList<List<Addressee>>();
        for (var from = 0; from < all.size(); from += batchSize) {
            out.add(all.subList(from, Math.min(all.size(), from + batchSize)));
        }
        return out;
    }

    int sendBatch(List<Addressee> batch, Kind kind, int version) {
        var result = transactions.execute(status -> {
            var now = clock.instant();
            var told = 0;
            for (var a : batch) {
                // The row and the message commit together: whoever writes the row is who sends.
                if (notifications.markSent(a.item().itemId(), version, a.employee().userId(), kind, Channel.IN_APP, now)) {
                    sender.send(new NotificationSender.ReaderNotification(kind, a.item().itemId(), version,
                            a.employee().userId(), title(kind, a.item()), body(kind, a.item()),
                            LINK + a.item().itemId(), now));
                    told++;
                }
            }
            return told;
        });
        return result == null ? 0 : result;
    }

    /**
     * One message per batch and language (Bcc), as the newsletter does. Sent first and written down
     * after: a relay that does not take it is tried again; the price is a repeat if the service dies in
     * between.
     */
    int mailBatch(List<Addressee> batch, Kind kind, int version) {
        var item = batch.getFirst().item();
        mailer.send(batch.stream().map(a -> a.employee().email()).toList(), title(kind, item), html(kind, item));
        transactions.executeWithoutResult(status -> {
            var now = clock.instant();
            batch.forEach(a -> notifications.markSent(item.itemId(), version, a.employee().userId(), kind, Channel.EMAIL, now));
        });
        return batch.size();
    }

    static String title(Kind kind, VisibleItem item) {
        var title = cut(item.title(), 450);
        if (kind == Kind.PUBLISHED) {
            return title;
        }
        return ("es".equals(item.language()) ? "Confirma la lectura: " : "Please confirm you have read: ") + title;
    }

    static String body(Kind kind, VisibleItem item) {
        var spanish = "es".equals(item.language());
        if (kind == Kind.CONFIRMATION_DUE) {
            var day = item.confirmationDeadline() == null ? "" : DAY.format(item.confirmationDeadline());
            return spanish ? "Tienes hasta el " + day + " para confirmar que lo has leído."
                    : "You have until " + day + " to confirm that you have read it.";
        }
        var summary = item.summary();
        return summary == null || summary.isBlank() ? (spanish ? "Hay algo nuevo en la intranet para ti." : "There is something new on the intranet for you.")
                : cut(summary, 1000);
    }

    String html(Kind kind, VisibleItem item) {
        var spanish = "es".equals(item.language());
        return "<div style=\"font-family:sans-serif;max-width:640px\"><h2><a href=\""
                + NewsletterService.esc(portalUrl + "/noticias/" + item.itemId()) + "\">"
                + NewsletterService.esc(title(kind, item)) + "</a></h2><p>" + NewsletterService.esc(body(kind, item))
                + "</p><p style=\"color:#888;font-size:12px\">"
                + (spanish ? "Puedes dejar de recibir estos correos en «Mis preferencias» de la intranet."
                : "You can stop these e-mails in «Mis preferencias» on the intranet.")
                + "</p></div>";
    }

    static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    // ── what closes them ────────────────────────────────────────────────────────────────────────

    /** The reader has opened the item: its news is no longer waiting for them. Never in «ver como». */
    public void opened(Reader reader, String itemId) {
        close(reader, itemId, Kind.PUBLISHED);
    }

    /** The reader has confirmed the item: nothing to remind them of. */
    public void confirmed(Reader reader, String itemId) {
        close(reader, itemId, Kind.CONFIRMATION_DUE);
    }

    void close(Reader reader, String itemId, Kind kind) {
        if (reader.viewingAs() != null) {
            return;
        }
        var open = notifications.openFor(reader.userId(), itemId, kind);
        if (!open.isEmpty()) {
            resolve(open, reader.userId());
        }
    }

    /** Closes what is about an item no longer readable, or has been waiting too long for anyone to care. */
    public void closeStale() {
        var now = clock.instant();
        for (var itemId : notifications.itemsWithOpen()) {
            var readable = items.find(itemId).flatMap(Item::publishedVersion)
                    .filter(v -> v.content().expireAt() == null || v.content().expireAt().isAfter(now)).isPresent();
            if (!readable) {
                resolve(notifications.openOf(itemId, CLOSE_BATCH), "intranet");
            }
        }
        resolve(notifications.openOlderThan(now.minus(closeAfter), CLOSE_BATCH), "intranet");
    }

    void resolve(List<NotificationLog.Open> open, String by) {
        if (open.isEmpty()) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            var now = clock.instant();
            for (var o : open) {
                notifications.resolved(o, now);
                sender.resolve(NotificationSender.subject(o.kind(), o.itemId(), o.userId()), by, now);
            }
        });
    }
}
