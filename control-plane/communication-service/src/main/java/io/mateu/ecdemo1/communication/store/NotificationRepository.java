package io.mateu.ecdemo1.communication.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String>, JpaSpecificationExecutor<Notification> {

    boolean existsByDedupKey(String dedupKey);

    List<Notification> findByStatusAndAttemptsLessThan(DeliveryStatus status, int attempts);

    List<Notification> findAllByOrderByRequestedAtDesc();

    List<Notification> findByChatStatusAndChatAttemptsLessThan(DeliveryStatus status, int attempts);
}
