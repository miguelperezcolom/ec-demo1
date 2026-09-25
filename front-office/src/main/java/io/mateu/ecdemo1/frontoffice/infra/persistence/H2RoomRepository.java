package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Repository;

/** H2 adapter of the {@link RoomRepository} port. */
@Repository
class H2RoomRepository implements RoomRepository {

  private final RoomCrud crud;
  private final JdbcAggregateTemplate template;

  H2RoomRepository(RoomCrud crud, JdbcAggregateTemplate template) {
    this.crud = crud;
    this.template = template;
  }

  /**
   * A stay the integration writes has no room until the desk assigns one: asked for the room of such a
   * stay, there is none — it used to throw, and the whole reservation screen with it.
   */
  @Override
  public Optional<Room> findByNumber(String number) {
    if (number == null || number.isBlank()) {
      return Optional.empty();
    }
    return crud.findById(number);
  }

  @Override
  public List<Room> findAll() {
    return crud.findAll();
  }

  @Override
  public List<Room> findByFloor(int floor) {
    return crud.findByFloorOrderByNumberAsc(floor);
  }

  @Override
  public Room save(Room room) {
    return crud.existsById(room.number()) ? crud.save(room) : template.insert(room);
  }
}
