package io.mateu.ecdemo1.communication.send;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.inbox.Inbox;
import io.mateu.ecdemo1.communication.store.Announcement;
import io.mateu.ecdemo1.communication.store.AnnouncementRepository;
import io.mateu.ecdemo1.communication.store.AnnouncementStatus;
import io.mateu.ecdemo1.communication.store.InboxItem;
import io.mateu.ecdemo1.communication.store.InboxItemRepository;
import io.mateu.ecdemo1.communication.store.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Everything that enters an inbox — a notification, a task of the forms engine — is also told where
 * people are: each Google Chat space, and the browser of everyone with one of its roles who allowed
 * notifications (Web Push). Off the consumer thread: an item is first handed out, one announcement
 * per channel, and each one is sent — and retried with growing waits — on its own.
 *
 * <p>Items that were already there when this started are not announced: a deployment does not
 * replay the inbox into the chat spaces.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Announcements {

    static final Duration FIRST_RETRY = Duration.ofSeconds(30);

    final InboxItemRepository items;
    final AnnouncementRepository announcements;
    final PushSubscriptionRepository subscriptions;
    final GoogleChat chat;
    final WebPush push;
    final CommunicationProperties properties;
    final Clock clock;
    final Instant startedAt = Instant.now();

    @Scheduled(fixedDelayString = "${communication.announce-every:5s}")
    @Transactional
    public void handOut() {
        for (var item : items.findTop50ByAnnouncedAtIsNullOrderByCreatedAtAsc()) {
            // What was there before this started, or was resolved before it went, is left alone.
            if (item.isOpen() && item.createdAt != null && item.createdAt.isAfter(startedAt.minus(Duration.ofMinutes(1)))) {
                for (var space = 1; space <= chat.webhooks().size(); space++) {
                    pending(item, "chat:" + space);
                }
                if (push.configured()) {
                    subscriptions.findAll().stream()
                            .filter(s -> sees(item, s.roles))
                            .forEach(s -> pending(item, "push:" + s.id));
                }
            }
            item.announcedAt = clock.instant();
            items.save(item);
        }
    }

    @Scheduled(fixedDelayString = "${communication.announce-every:5s}", initialDelay = 2500)
    @Transactional
    public void send() {
        for (var a : announcements.findTop100ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
                List.of(AnnouncementStatus.PENDING, AnnouncementStatus.FAILED), clock.instant())) {
            var item = items.findById(a.itemId).orElse(null);
            if (item == null || !item.isOpen()) {
                a.status = AnnouncementStatus.SKIPPED;
                announcements.save(a);
                continue;
            }
            a.attempts++;
            try {
                deliver(a, item);
                a.status = AnnouncementStatus.SENT;
                a.sentAt = clock.instant();
                a.lastError = null;
                log.info("Announced {} on {}: {}", item.type, a.channel, item.title);
            } catch (WebPush.Gone e) {
                a.status = AnnouncementStatus.GONE;
                a.lastError = e.getMessage();
                subscriptions.deleteById(a.channel.substring("push:".length()));
                log.info("A browser unsubscribed ({}): dropped", a.channel);
            } catch (RuntimeException e) {
                a.lastError = e.getMessage();
                a.status = AnnouncementStatus.FAILED;
                // Out of attempts: no next one, and the query that picks what is due never sees it again.
                a.nextAttemptAt = a.attempts >= properties.maxAttempts() ? null
                        : clock.instant().plus(FIRST_RETRY.multipliedBy(1L << (a.attempts - 1)));
                log.warn("Could not announce {} on {} (attempt {}): {}", item.id, a.channel, a.attempts, e.getMessage());
            }
            announcements.save(a);
        }
    }

    void deliver(Announcement a, InboxItem item) {
        if (a.channel.startsWith("chat:")) {
            chat.post(Integer.parseInt(a.channel.substring("chat:".length())), item);
        } else if (a.channel.startsWith("push:")) {
            var subscription = subscriptions.findById(a.channel.substring("push:".length()))
                    .orElseThrow(() -> new WebPush.Gone("Unsubscribed"));
            push.send(subscription, item);
            subscription.lastSentAt = clock.instant();
            subscriptions.save(subscription);
        }
    }

    void pending(InboxItem item, String channel) {
        var a = new Announcement();
        a.id = item.id + "|" + channel;
        a.itemId = item.id;
        a.channel = channel;
        a.status = AnnouncementStatus.PENDING;
        a.nextAttemptAt = clock.instant();
        announcements.save(a);
    }

    /** Whether a person with these roles sees the item in their inbox. */
    static boolean sees(InboxItem item, String roles) {
        var theirs = roles == null ? List.<String>of() : Arrays.stream(roles.split(",")).map(String::trim).toList();
        for (var role : item.roles == null ? new String[0] : item.roles.split(",")) {
            if (Inbox.EVERYONE.equals(role.trim()) || theirs.contains(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
