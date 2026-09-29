package io.mateu.ecdemo1.frontoffice.infra.pms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.mateu.ecdemo1.frontoffice.domain.room.RoomOccupancy;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Which of the PMS's rooms the desk is offered as ready: vacant, and as the property's housekeeping
 * must say for it to assign them — XMAR, inspected only (FOF00081 on a clean one). The others are shown
 * with why, the vacant ones still selectable on purpose.
 */
class PmsRoomsTest {

  final PmsRooms xmar = new PmsRooms(mock(PmsCatalogue.class), "XMAR", "", "Inspected");

  static PmsRooms.OperaRoom opera(String id, String housekeeping, String frontOffice) {
    return new PmsRooms.OperaRoom(id, "SJMB", housekeeping, frontOffice);
  }

  @Test
  void anInspectedVacantRoomIsReadyAndACleanOneIsNotAtXmar() {
    var inspected = PmsRooms.room("5142", "SJMB", opera("5142", "Inspected", "Vacant"), true, xmar.readyStatuses);
    var clean = PmsRooms.room("206", "SJMB", opera("206", "Clean", "Vacant"), true, xmar.readyStatuses);
    var dirty = PmsRooms.room("5138", "SJMB", opera("5138", "Dirty", "Vacant"), true, xmar.readyStatuses);
    var occupied = PmsRooms.room("5144", "SJMB", opera("5144", "Inspected", "Occupied"), true, xmar.readyStatuses);
    var outOfOrder = PmsRooms.room("5146", "SJMB", opera("5146", "OutOfOrder", "Vacant"), true, xmar.readyStatuses);

    assertThat(xmar.ready(inspected)).isTrue();
    assertThat(inspected.maintenanceNote()).isNull();
    // Vacant but not ready: offered with why, still selectable.
    assertThat(xmar.ready(clean)).isFalse();
    assertThat(clean.occupancy()).isEqualTo(RoomOccupancy.FREE);
    assertThat(clean.maintenanceNote()).isEqualTo("Limpia, sin inspeccionar en Opera");
    assertThat(dirty.maintenanceNote()).isEqualTo("Sucia en Opera");
    // Cannot be given.
    assertThat(xmar.ready(occupied)).isFalse();
    assertThat(occupied.assignable()).isFalse();
    assertThat(outOfOrder.assignable()).isFalse();
    assertThat(outOfOrder.maintenanceNote()).isEqualTo("Fuera de servicio en Opera");
    // Ready first, then the vacant ones not ready, then the ones that cannot be given.
    assertThat(xmar.rank(inspected)).isLessThan(xmar.rank(clean));
    assertThat(xmar.rank(clean)).isLessThan(xmar.rank(occupied));
  }

  @Test
  void aPropertyThatDoesNotInspectTakesCleanRooms() {
    var lenient = new PmsRooms(mock(PmsCatalogue.class), "XMAR", "", "Clean, Inspected");
    assertThat(lenient.readyStatuses).isEqualTo(Set.of("Clean", "Inspected"));

    var clean = PmsRooms.room("206", "STDK", opera("206", "Clean", "Vacant"), true, lenient.readyStatuses);

    assertThat(clean.maintenanceNote()).isNull();
    assertThat(lenient.ready(clean)).isTrue();
  }

  @Test
  void withoutTheConnectorTheReadinessIsUnknownNotReady() {
    var readiness = xmar.readiness("5142");

    assertThat(readiness.known()).isFalse();
    assertThat(readiness.ready()).isFalse();
    assertThat(readiness.reason()).isEqualTo("Estado de Opera no disponible");
  }
}
