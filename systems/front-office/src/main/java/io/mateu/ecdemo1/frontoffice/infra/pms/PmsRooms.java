package io.mateu.ecdemo1.frontoffice.infra.pms;

import io.mateu.ecdemo1.frontoffice.domain.room.HousekeepingStatus;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomOccupancy;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
 * if it must. What it answers is kept a few seconds ({@link #TTL}): a screen re-renders on every click,
 * and Opera's housekeeping does not change that fast; «Comprobar» asks again ({@link #refresh}).
 *
 * <p><b>Ready</b> is what the property assigns: vacant, and with a housekeeping status it takes
 * ({@code frontoffice.pms-ready-statuses}) — XMAR assigns only inspected rooms (FOF00081 «Room … is
 * Clean (CL)» on a clean one). The ready rooms are offered first; the vacant ones not ready yet are
 * still shown, with why, for the desk to choose one on purpose (Opera may refuse it); occupied and out
 * of order ones cannot be chosen.
 */
@Service
public class PmsRooms {

  static final Logger log = LoggerFactory.getLogger(PmsRooms.class);

  /** How long an answer of Opera is reused. */
  static final Duration TTL = Duration.ofSeconds(20);

  record OperaRoom(String roomId, String roomType, String housekeeping, String frontOffice) {}

  /**
   * Whether a room is ready for a stay now, as Opera says: {@code known} false when Opera did not
   * answer; {@code state} Opera's words (Inspected · Vacant); {@code reason} why it is not ready.
   */
  public record Readiness(String roomNumber, boolean known, boolean ready, String state, String reason) {}

  record Cached<T>(T value, Instant at) {}

  final PmsCatalogue catalogue;
  final String pmsHotel;
  final RestClient pms;
  final Set<String> readyStatuses;
  final Clock clock = Clock.systemUTC();
  final Map<String, Cached<Map<String, OperaRoom>>> byType = new ConcurrentHashMap<>();
  final Map<String, Cached<Readiness>> byRoom = new ConcurrentHashMap<>();

  public PmsRooms(PmsCatalogue catalogue, @Value("${frontoffice.pms-hotel:XMAR}") String pmsHotel,
      @Value("${frontoffice.pms-integration-url:}") String pmsIntegrationUrl,
      @Value("${frontoffice.pms-ready-statuses:Inspected}") String readyStatuses) {
    this.catalogue = catalogue;
    this.pmsHotel = pmsHotel;
    this.readyStatuses = Arrays.stream((readyStatuses == null || readyStatuses.isBlank() ? "Inspected" : readyStatuses)
        .split(",")).map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toUnmodifiableSet());
    if (pmsIntegrationUrl == null || pmsIntegrationUrl.isBlank()) {
      this.pms = null;
    } else {
      var factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(3));
      factory.setReadTimeout(Duration.ofSeconds(8));
      this.pms = RestClient.builder().baseUrl(pmsIntegrationUrl).requestFactory(factory).build();
    }
  }

  /**
   * The PMS's rooms of this room type (its code, or its words as the stay shows it), ready ones first;
   * none if the catalogue has none.
   */
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
    return rooms.stream().map(e -> room(e.code(), label, opera.get(e.code()), !opera.isEmpty(), readyStatuses))
        .sorted(Comparator.comparingInt(this::rank)).toList();
  }

  /** Ready first, then the vacant ones not ready yet, then the ones that cannot be given. */
  int rank(Room room) {
    return ready(room) ? 0 : room.assignable() ? 1 : 2;
  }

  /** Whether the room can be given now as it is: vacant, and as the property's housekeeping requires. */
  public boolean ready(Room room) {
    return room.assignable() && room.maintenanceNote() == null && readyStatuses.contains(operaWord(room.housekeeping()));
  }

  /** A room of the PMS's catalogue, by number — without asking Opera its state. */
  public java.util.Optional<Room> find(String number) {
    return catalogue.list(pmsHotel, CatalogueType.ROOM).stream().filter(e -> e.code().equals(number)).findFirst()
        .map(e -> room(e.code(), e.extra() == null ? null
            : catalogue.describe(pmsHotel, CatalogueType.ROOM_TYPE, e.extra()).orElse(e.extra()), null, false, readyStatuses));
  }

  /**
   * Whether the room a stay was given is ready in Opera now — one room asked, kept a few seconds: what
   * the stay's page says («habitación lista»).
   */
  public Readiness readiness(String roomNumber) {
    if (roomNumber == null || roomNumber.isBlank()) {
      return new Readiness(roomNumber, false, false, null, "Sin habitación asignada");
    }
    var cached = byRoom.get(roomNumber);
    if (cached != null && cached.at().plus(TTL).isAfter(clock.instant())) {
      return cached.value();
    }
    var answer = ask(roomNumber);
    if (answer.known()) {
      byRoom.put(roomNumber, new Cached<>(answer, clock.instant()));
    }
    return answer;
  }

  /** As {@link #readiness}, asking Opera again now (the desk's «Comprobar»). */
  public Readiness refresh(String roomNumber) {
    if (roomNumber != null) {
      byRoom.remove(roomNumber);
    }
    byType.clear();
    return readiness(roomNumber);
  }

  Readiness ask(String roomNumber) {
    if (pms == null) {
      return new Readiness(roomNumber, false, false, null, "Estado de Opera no disponible");
    }
    try {
      var answer = pms.get().uri(b -> b.path("/front-office/rooms").queryParam("hotelId", pmsHotel)
          .queryParam("roomId", roomNumber).build()).retrieve().body(OperaRoom[].class);
      var opera = answer == null ? null : Arrays.stream(answer).filter(r -> roomNumber.equals(r.roomId())).findFirst()
          .orElse(null);
      if (opera == null) {
        return new Readiness(roomNumber, true, false, null, "Opera no la lista");
      }
      var room = room(roomNumber, opera.roomType(), opera, true, readyStatuses);
      var state = (opera.housekeeping() == null ? "?" : opera.housekeeping()) + " · "
          + (opera.frontOffice() == null ? "?" : opera.frontOffice());
      return new Readiness(roomNumber, true, ready(room), state,
          ready(room) ? null : room.maintenanceNote() != null ? room.maintenanceNote() : "Ocupada en Opera");
    } catch (RuntimeException e) {
      log.warn("Opera's state of room {} not available: {}", roomNumber, e.getMessage());
      return new Readiness(roomNumber, false, false, null, "Estado de Opera no disponible");
    }
  }

  Map<String, OperaRoom> opera(String roomTypeCode) {
    var cached = byType.get(roomTypeCode);
    if (cached != null && cached.at().plus(TTL).isAfter(clock.instant())) {
      return cached.value();
    }
    var byRoomId = new HashMap<String, OperaRoom>();
    if (pms == null) {
      return byRoomId;
    }
    try {
      var answer = pms.get().uri(b -> b.path("/front-office/rooms").queryParam("hotelId", pmsHotel)
          .queryParam("roomType", roomTypeCode).build()).retrieve().body(OperaRoom[].class);
      for (var r : answer == null ? new OperaRoom[0] : answer) {
        byRoomId.put(r.roomId(), r);
      }
      byType.put(roomTypeCode, new Cached<>(Map.copyOf(byRoomId), clock.instant()));
    } catch (RuntimeException e) {
      log.warn("Opera's state of the {} rooms not available: {}", roomTypeCode, e.getMessage());
    }
    return byRoomId;
  }

  static String operaWord(HousekeepingStatus status) {
    return switch (status) {
      case INSPECTED -> "Inspected";
      case CLEAN -> "Clean";
      case DIRTY -> "Dirty";
    };
  }

  static Room room(String number, String type, OperaRoom opera, boolean operaAnswered, Set<String> readyStatuses) {
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
    var outOfOrder = hk.equals("OutOfOrder") || hk.equals("OutOfService");
    // Not ready: said, so the desk picks a ready one — or this one on purpose, knowing Opera may refuse
    // it (XMAR refuses a clean one not yet inspected at the assignment: FOF00081 «Room … is Clean (CL)»).
    var note = outOfOrder ? "Fuera de servicio en Opera"
        : readyStatuses.contains(hk) ? null
        : switch (hk) {
          case "Clean" -> "Limpia, sin inspeccionar en Opera";
          case "Pickup" -> "Pendiente de repaso en Opera";
          case "Dirty" -> "Sucia en Opera";
          default -> "En Opera: " + (hk.isBlank() ? "sin estado" : hk);
        };
    return new Room(number, floor, type, occupied || outOfOrder ? RoomOccupancy.OCCUPIED : RoomOccupancy.FREE,
        housekeeping, note);
  }
}
