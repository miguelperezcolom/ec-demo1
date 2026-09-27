package io.mateu.ecdemo1.communication.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, String> {

    List<Announcement> findTop100ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(List<AnnouncementStatus> statuses, Instant before);
}
