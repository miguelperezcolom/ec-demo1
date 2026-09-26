package io.mateu.ecdemo1.communication.inbox;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.routing.Routing;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.InboxItem;
import io.mateu.ecdemo1.communication.store.InboxItemRepository;
import io.mateu.ecdemo1.communication.store.InboxSeen;
import io.mateu.ecdemo1.communication.store.InboxSeenRepository;
import io.mateu.ecdemo1.communication.store.Resolution;
import io.mateu.ecdemo1.communication.store.ResolutionRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationResolved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What waits for each person: the notifications the recipients with an inbox send their way, and the
 * tasks of the forms engine, until what they are about is resolved. A person sees what is theirs by
 * name or by any of their roles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Inbox {

    public static final String EVERYONE = "*";

    final InboxItemRepository items;
    final InboxSeenRepository seen;
    final ResolutionRepository resolutions;
    final Routing routing;
    final CommunicationProperties properties;
    final Clock clock;

    /**
     * A notification, into the inbox of the recipients with an inbox that want it. Once per
     * notification. Nobody's, and it is still kept — resolved or not, it is the record of what was asked.
     */
    @Transactional
    public void post(NotificationRequested n) {
        if (items.existsById(n.notificationId())) {
            return;
        }
        var item = new InboxItem();
        item.id = n.notificationId();
        item.kind = InboxItem.Kind.ACTION.name();
        item.type = n.type().name();
        item.hotelCode = n.hotelCode();
        item.subject = n.subject();
        item.title = n.title();
        item.body = n.body();
        item.link = n.link();
        var plan = routing.plan(n.type().name(), n.hotelCode());
        item.roles = Recipient.join(plan.inboxRoles());
        item.users = Recipient.join(plan.inboxUsers());
        item.urgent = plan.urgent();
        item.createdAt = n.requestedAt() == null ? clock.instant() : n.requestedAt();
        closeIfAlreadyResolved(item);
        items.save(item);
    }

    /**
     * A task of the forms engine: open while it waits for someone, gone once completed or cancelled.
     * Its people are the task's own — the roles its form requires, everyone's if none — and, besides,
     * whoever a recipient with an inbox that wants tasks names.
     */
    @Transactional
    public void task(HumanTask t) {
        var id = "task/" + t.taskId();
        if (!t.open()) {
            resolve(id, t.userId(), t.at() == null ? clock.instant() : t.at().plusMillis(1));
            return;
        }
        var item = items.findById(id).orElseGet(InboxItem::new);
        item.id = id;
        item.kind = InboxItem.Kind.TASK.name();
        item.type = "TASK";
        item.subject = id;
        item.title = t.formName() == null || t.formName().isBlank() ? "Task " + t.formId() : t.formName();
        item.body = "A form waits to be filled in" + (t.userId() == null || t.userId().isBlank() ? "" : ", by " + t.userId())
                + ". Process " + t.processId() + ", step " + t.stepId() + ".";
        item.link = properties.inbox().tasksLink();
        var plan = routing.plan(Recipient.TASK, null);
        var roles = new java.util.LinkedHashSet<String>(t.requiredRoles() == null || t.requiredRoles().isEmpty()
                ? List.of(EVERYONE) : t.requiredRoles());
        roles.addAll(plan.inboxRoles());
        item.roles = Recipient.join(roles);
        item.users = Recipient.join(plan.inboxUsers());
        if (item.createdAt == null) {
            item.createdAt = t.at() == null ? clock.instant() : t.at();
        }
        closeIfAlreadyResolved(item);
        items.save(item);
    }

    /** Closes, in every inbox, what was waiting on this subject until now. */
    @Transactional
    public int resolve(String subject, String by, Instant at) {
        var resolution = resolutions.findById(subject).orElseGet(Resolution::new);
        if (resolution.resolvedAt == null || resolution.resolvedAt.isBefore(at)) {
            resolution.subject = subject;
            resolution.resolvedAt = at;
            resolution.resolvedBy = by;
            resolutions.save(resolution);
        }
        var open = items.findBySubjectAndResolvedAtIsNullAndCreatedAtBefore(subject, at);
        open.forEach(item -> {
            item.resolvedAt = at;
            item.resolvedBy = by;
        });
        items.saveAll(open);
        if (!open.isEmpty()) {
            log.info("{} resolved by {}: {} inbox item(s) closed", subject, by, open.size());
        }
        return open.size();
    }

    @Transactional
    public void resolved(NotificationResolved r) {
        resolve(r.subject(), r.resolvedBy(), r.resolvedAt() == null ? clock.instant() : r.resolvedAt());
    }

    /** What waits for a person with these roles, newest first. */
    public List<InboxItem> openFor(Collection<String> roles) {
        return openFor(roles, null);
    }

    /** What waits for this person — theirs by name, or by one of these roles — newest first. */
    public List<InboxItem> openFor(Collection<String> roles, String username) {
        return items.findByResolvedAtIsNullOrderByCreatedAtDesc().stream().filter(i -> visibleTo(i, roles, username)).toList();
    }

    public java.util.Optional<InboxItem> find(String id) {
        return id == null ? java.util.Optional.empty() : items.findById(id);
    }

    /** Marks these items as seen by this person. Nobody signed in, nothing to mark. */
    @Transactional
    public int markSeen(Collection<String> itemIds, String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        var already = seenBy(username);
        var now = clock.instant();
        var marks = itemIds.stream().filter(id -> id != null && !already.contains(id)).distinct().map(id -> {
            var mark = new InboxSeen();
            mark.id = InboxSeen.id(id, username);
            mark.itemId = id;
            mark.username = username;
            mark.seenAt = now;
            return mark;
        }).toList();
        seen.saveAll(marks);
        return marks.size();
    }

    /** The ids of the items this person has seen. */
    public Set<String> seenBy(String username) {
        if (username == null || username.isBlank()) {
            return Set.of();
        }
        return seen.findByUsername(username).stream().map(s -> s.itemId).collect(Collectors.toSet());
    }

    public static boolean visibleTo(InboxItem item, Collection<String> roles, String username) {
        if (username != null && Recipient.split(item.users).contains(username)) {
            return true;
        }
        return Recipient.split(item.roles).stream().anyMatch(role -> EVERYONE.equals(role) || roles.contains(role));
    }

    /** A notification that overtook nothing: what it is about was resolved after it was requested. */
    void closeIfAlreadyResolved(InboxItem item) {
        if (item.subject == null) {
            return;
        }
        resolutions.findById(item.subject)
                .filter(r -> r.resolvedAt != null && item.createdAt.isBefore(r.resolvedAt))
                .ifPresent(r -> {
                    item.resolvedAt = r.resolvedAt;
                    item.resolvedBy = r.resolvedBy;
                });
    }
}
