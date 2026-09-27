package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Spring Data JDBC repository backing {@link H2StayRepository}. */
interface StayCrud extends ListCrudRepository<StayEntity, String> {

  List<StayEntity> findByStatusOrderByCheckInAsc(StayStatus status);

  List<StayEntity> findByStatusOrderByCheckOutAsc(StayStatus status);

  /** The stays a person is in: as the guest the stay belongs to, or in the room with them. */
  @Query("select s.id from stay s where s.guest_id = :person"
      + " or s.id in (select c.stay_id from stay_companion c where c.companion_id = :person)")
  List<String> idsOfPerson(@Param("person") String person);
}
