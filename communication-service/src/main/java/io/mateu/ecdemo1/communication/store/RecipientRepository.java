package io.mateu.ecdemo1.communication.store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientRepository extends JpaRepository<Recipient, String> {
}
