package io.mateu.ecdemo1.communication.application;

import io.mateu.ecdemo1.communication.store.Notification;
import io.mateu.ecdemo1.communication.store.NotificationRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The read side of the notifications: one page of those a text matches, asked of the database, newest
 * first unless the page asks for another order; and one of them.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueries {

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "requestedAt");

    final NotificationRepository notifications;

    /** Those whose title, hotel or type has {@code text} in it, ignoring case; every one for no text. */
    public Page<Notification> find(String text, Pageable pageable) {
        return notifications.findAll(matching(text), pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
    }

    public Optional<Notification> byId(String id) {
        return notifications.findById(id);
    }

    static Specification<Notification> matching(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }
            var like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
            var any = new ArrayList<Predicate>();
            any.add(cb.like(cb.lower(root.get("title")), like));
            any.add(cb.like(cb.lower(root.get("hotelCode")), like));
            // The type is an enum column: which types the text names is worked out here, not in SQL.
            var types = typesNamedBy(text);
            if (!types.isEmpty()) {
                any.add(root.get("type").in(types));
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
    }

    /** The types whose name has the text in it, ignoring case. */
    static List<NotificationType> typesNamedBy(String text) {
        var wanted = text.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(NotificationType.values())
                .filter(t -> t.name().toLowerCase(Locale.ROOT).contains(wanted)).toList();
    }
}
