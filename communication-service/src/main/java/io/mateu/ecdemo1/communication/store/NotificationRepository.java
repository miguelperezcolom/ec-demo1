package io.mateu.ecdemo1.communication.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    boolean existsByDedupKey(String dedupKey);

    List<Notification> findByStatusAndAttemptsLessThan(DeliveryStatus status, int attempts);

    List<Notification> findAllByOrderByRequestedAtDesc();
}
