package io.mateu.ecdemo1.customerhistory.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CustomerAliasRepository extends JpaRepository<CustomerAlias, String> {

    /** The codes merged straight into these ones — on the survivorId index. */
    List<CustomerAlias> findBySurvivorIdIn(Collection<String> survivorIds);
}
