package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2StayRepository}. */
interface StayCrud extends ListCrudRepository<Stay, String> {

  List<Stay> findByStatusOrderByCheckInAsc(StayStatus status);

  List<Stay> findByStatusOrderByCheckOutAsc(StayStatus status);
}
