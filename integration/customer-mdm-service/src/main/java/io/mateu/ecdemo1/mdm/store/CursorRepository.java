package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CursorRepository extends JpaRepository<Cursor, String> {
}
