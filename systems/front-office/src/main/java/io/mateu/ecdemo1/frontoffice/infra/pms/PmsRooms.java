package io.mateu.ecdemo1.frontoffice.infra.pms;

import io.mateu.ecdemo1.frontoffice.domain.room.HousekeepingStatus;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomOccupancy;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueType;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * The rooms the desk can give a stay at the check-in: the PMS's — the master of the stay — of the
 * stay's room type, from the catalogue the pms-fo integration gave this front office, each with what
 * Opera's housekeeping says of it now (clean, inspected, dirty; vacant or occupied). The check-in
 * assigns the chosen one in Opera, and a room Opera would refuse is better not offered.
 *
 * <p>Opera's state is a query for this screen, answered now through the connector
 * ({@code GET /front-office/rooms} of pms-integration-service) — a query, not an order: orders go by
 * events. If it does not answer, the rooms are offered without it, and Opera says no at the check-in
 * if it must.
 */
@Service
public class PmsRooms {

  static final Logger log = LoggerFactory.getLogger(PmsRooms.class);

  record OperaRoom(String roomId, String roomType, String housekeeping, String frontOffice) {}

  final PmsCatalogue catalogue;
  final String pmsHotel;
  final RestClient pms;

  public PmsRooms(PmsCatalogue catalogue, @Value("${frontoffice.pms-hotel:XMAR}") String pmsHotel,
      @Value("${frontoffice.pms-integration-url:}") String pmsIntegrationUrl) {
    this.catalogue = catalogue;
    this.pmsHotel = pmsHotel;
    if (pmsIntegrationUrl == null || pmsIntegrationUrl.isBlank()) {
      this.pms = null;
    } else {
      var factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(3));
      factory.setReadTimeout(Duration.ofSeconds(8));
      this.pms = RestClient.builder().baseUrl(pmsIntegrationUrl).requestFactory(factory).build();
    }
  }

  /** The PMS's rooms of this room type (its code, or its words as the stay shows it); none if the catalogue has none. */
  public List<Room> ofType(String roomType) {
    if (roomType == null || roomType.isBlank()) {
      return List.of();
    }
    var code = catalogue.list(pmsHotel, CatalogueType.ROOM_TYPE).stream()
        .filter(e -> roomType.equals(e.code()) || roomType.equalsIgnoreCase(e.description()))
        .map(e -> e.code()).findFirst().orElse(null);
    if (code == null) {
      return List.of();
    }
    var rooms = catalogue.list(pmsHotel, CatalogueType.ROOM).stream().filter(e -> code.equals(e.extra())).toList();
    if (rooms.isEmpty()) {
      return List.of();
    }
    var opera = opera(code);
    var label = catalogue.describe(pmsHotel, CatalogueType.ROOM_TYPE, code).orElse(code);
    return rooms.stream().map(e -> room(e.code(), label, opera.get(e.code()), !opera.isEmpty())).toList();
  }

  /** A room of the PMS's catalogue, by number — without asking Opera its state. */
  public java.util.Optional<Room> find(String number) {
    return catalogue.list(pmsHotel, CatalogueType.ROOM).stream().filter(e -> e.code().equals(number)).findFirst()
        .map(e -> room(e.code(), e.extra() == null ? null
            : catalogue.describe(pmsHotel, CatalogueType.ROOM_TYPE, e.extra()).orElse(e.extra()), null, false));
  }

  Map<String, OperaRoom> opera(String roomTypeCode) {
    var byRoom = new HashMap<String, OperaRoom>();
    if (pms == null) {
      return byRoom;
    }
    try {
      var answer = pms.get().uri(b -> b.path("/front-office/rooms").queryParam("hotelId", pmsHotel)
          .queryParam("roomType", roomTypeCode).build()).retrieve().body(OperaRoom[].class);
      for (var r : answer == null ? new OperaRoom[0] : answer) {
        byRoom.put(r.roomId(), r);
      }
    } catch (RuntimeException e) {
      log.warn("Opera's state of the {} rooms not available: {}", roomTypeCode, e.getMessage());
    }
    return byRoom;
  }

  static Room room(String number, String type, OperaRoom opera, boolean operaAnswered) {
    var floor = 0;
    try {
      floor = number.length() > 2 ? Integer.parseInt(number.substring(0, number.length() - 2)) : 0;
    } catch (NumberFormatException ignored) {
      // a room with letters: no floor
    }
    if (opera == null) {
      // Opera answered and did not list it (out of order, say): not offered.
      return new Room(number, floor, type, operaAnswered ? RoomOccupancy.OCCUPIED : RoomOccupancy.FREE,
          HousekeepingStatus.CLEAN, operaAnswered ? "Opera no la lista" : "Estado de Opera no disponible");
    }
    var hk = opera.housekeeping() == null ? "" : opera.housekeeping();
    var housekeeping = switch (hk) {
      case "Inspected" -> HousekeepingStatus.INSPECTED;
      case "Clean" -> HousekeepingStatus.CLEAN;
      default -> HousekeepingStatus.DIRTY;
    };
    var occupied = "Occupied".equalsIgnoreCase(opera.frontOffice());
    // A property that works with inspected rooms (XMAR does) refuses a clean one not yet inspected at
    // the assignment (FOF00081 «Room … is Clean (CL)»): said, so the desk picks an inspected one.
    var note = hk.equals("OutOfOrder") || hk.equals("OutOfService") ? "Fuera de servicio en Opera"
        : hk.equals("Pickup") ? "Pendiente de repaso en Opera"
        : hk.equals("Clean") ? "Limpia, sin inspeccionar en Opera" : null;
    return new Room(number, floor, type, occupied || note != null && note.startsWith("Fuera") ? RoomOccupancy.OCCUPIED
        : RoomOccupancy.FREE, housekeeping, note);
  }
}
