package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2RoomRepository}. */
interface RoomCrud extends ListCrudRepository<Room, String> {

  List<Room> findByFloorOrderByNumberAsc(int floor);
}
