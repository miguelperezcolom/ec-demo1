package io.mateu.ecdemo1.frontoffice.infra.persistence;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2RoomRepository}. */
interface RoomCrud extends ListCrudRepository<RoomEntity, String> {
  List<RoomEntity> findByFloorOrderByNumberAsc(int floor);
}
