package io.mateu.ecdemo1.mdm.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelUpdateRepository extends JpaRepository<HotelUpdate, String> {

    List<HotelUpdate> findTop50ByDoneAtIsNullOrderByCreatedAtAsc();
}
