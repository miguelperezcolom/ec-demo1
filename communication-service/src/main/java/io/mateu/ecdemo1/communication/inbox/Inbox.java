package io.mateu.ecdemo1.communication.inbox;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.store.InboxItem;
import io.mateu.ecdemo1.communication.store.InboxItemRepository;
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

/**
 * What waits for each role: every notification, and every task of the forms engine, until what it
 * is about is resolved. A person sees what any of their roles sees to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Inbox {

    public static final String EVERYONE = "*";

    final InboxItemRepository items;
    final ResolutionRepository resolutions;
    final CommunicationProperties properties;
    final Clock clock;

    /** A notification, into the inbox of the roles that see to its kind. Once per notification. */
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
        item.roles = String.join(",", properties.inbox().rolesFor(n.type().name()));
        item.urgent = properties.inbox().isUrgent(n.type().name());
        item.createdAt = n.requestedAt() == null ? clock.instant() : n.requestedAt();
        closeIfAlreadyResolved(item);
        items.save(item);
    }

    /** A task of the forms engine: open while it waits for someone, gone once completed or cancelled. */
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
        item.roles = t.requiredRoles() == null || t.requiredRoles().isEmpty() ? EVERYONE : String.join(",", t.requiredRoles());
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
        return items.findByResolvedAtIsNullOrderByCreatedAtDesc().stream().filter(i -> visibleTo(i, roles)).toList();
    }

    static boolean visibleTo(InboxItem item, Collection<String> roles) {
        for (var role : item.roles == null ? new String[0] : item.roles.split(",")) {
            if (EVERYONE.equals(role.trim()) || roles.contains(role.trim())) {
                return true;
            }
        }
        return false;
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
