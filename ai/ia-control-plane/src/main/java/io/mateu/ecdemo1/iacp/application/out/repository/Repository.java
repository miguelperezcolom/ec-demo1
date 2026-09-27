package io.mateu.ecdemo1.iacp.application.out.repository;

import java.util.List;
import java.util.Optional;

/**
 * The write side of a catalogue. Four of these, one per aggregate, and the infrastructure decides
 * what backs them — see {@code infra/out/persistence}.
 */
public interface Repository<T, ID> {
    T save(T aggregate);
    Optional<T> findById(ID id);
    List<T> findAll();
    void deleteAllById(List<ID> ids);
    boolean existsById(ID id);
}
