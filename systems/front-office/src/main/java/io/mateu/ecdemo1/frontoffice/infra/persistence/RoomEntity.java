package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.room.HousekeepingStatus;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomOccupancy;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** How a {@link Room} is kept: a row of the {@code room} table, by its number. */
@Table("room")
record RoomEntity(
    @Id @Column("room_number") String number,
    int floor,
    String type,
    RoomOccupancy occupancy,
    HousekeepingStatus housekeeping,
    String maintenanceNote) {

  static RoomEntity of(Room r) {
    return new RoomEntity(r.number(), r.floor(), r.type(), r.occupancy(), r.housekeeping(), r.maintenanceNote());
  }

  Room toDomain() {
    return new Room(number, floor, type, occupancy, housekeeping, maintenanceNote);
  }
}
