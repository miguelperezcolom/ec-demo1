package io.mateu.ecdemo1.communication.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboxSeenRepository extends JpaRepository<InboxSeen, String> {

    List<InboxSeen> findByUsername(String username);
}
