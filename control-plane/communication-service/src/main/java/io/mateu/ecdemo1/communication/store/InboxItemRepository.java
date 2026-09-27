package io.mateu.ecdemo1.communication.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface InboxItemRepository extends JpaRepository<InboxItem, String> {

    List<InboxItem> findBySubjectAndResolvedAtIsNullAndCreatedAtBefore(String subject, Instant before);

    List<InboxItem> findByResolvedAtIsNullOrderByCreatedAtDesc();

    List<InboxItem> findTop50ByAnnouncedAtIsNullOrderByCreatedAtAsc();
}
