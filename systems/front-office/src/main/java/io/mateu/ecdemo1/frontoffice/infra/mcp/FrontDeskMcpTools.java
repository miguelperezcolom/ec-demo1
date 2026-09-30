package io.mateu.ecdemo1.frontoffice.infra.mcp;

import io.mateu.ecdemo1.frontoffice.application.CheckInService;
import io.mateu.ecdemo1.frontoffice.application.CheckOutService;
import io.mateu.ecdemo1.frontoffice.application.FolioService;
import io.mateu.ecdemo1.frontoffice.application.GuestNotices;
import io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNotice;
import io.mateu.ecdemo1.frontoffice.application.KardexService;
import io.mateu.ecdemo1.frontoffice.application.NoShowService;
import io.mateu.ecdemo1.frontoffice.application.RoomChangeService;
import io.mateu.ecdemo1.frontoffice.application.StayQueries;
import io.mateu.ecdemo1.frontoffice.application.WalkInService;
import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogItem;
import io.mateu.ecdemo1.frontoffice.domain.catalog.AddOnCatalogRepository;
import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInChecklist;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIn;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.crs.WalkInDesk;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The front desk as tools for the reception agent: what it reads — arrivals, departures, a stay, a
 * guest and their kárdex, a folio, the free rooms — and the desk's operations, each one of the
 * application layer's use cases. An operation is never done by the tool that asks for it: a
 * {@code prepare…} tool checks it and describes it, and {@link PendingConfirmations} carries it out
 * only once the person confirmed it, in a later message ({@link #confirmAction}).
 */
@Component
public class FrontDeskMcpTools {

  final StayQueries queries;
  final StayRepository stays;
  final GuestRepository guests;
  final FolioRepository folios;
  final RoomRepository rooms;
  final AddOnCatalogRepository addOns;
  final WalkIns walkIns;
  final CheckInService checkIn;
  final CheckOutService checkOut;
  final NoShowService noShows;
  final RoomChangeService roomChange;
  final FolioService folioService;
  final KardexService kardex;
  final WalkInService walkInService;
  final PendingConfirmations confirmations;
  final GuestNotices notices;
  final McpCaller caller;
  final io.mateu.ecdemo1.frontoffice.application.IncompleteCheckIns incomplete;

  public FrontDeskMcpTools(StayQueries queries, StayRepository stays, GuestRepository guests, FolioRepository folios,
                           RoomRepository rooms, AddOnCatalogRepository addOns, WalkIns walkIns,
                           CheckInService checkIn, CheckOutService checkOut, NoShowService noShows,
                           RoomChangeService roomChange, FolioService folioService, KardexService kardex,
                           WalkInService walkInService, PendingConfirmations confirmations, GuestNotices notices,
                           McpCaller caller, io.mateu.ecdemo1.frontoffice.application.IncompleteCheckIns incomplete) {
    this.incomplete = incomplete;
    this.queries = queries;
    this.stays = stays;
    this.guests = guests;
    this.folios = folios;
    this.rooms = rooms;
    this.addOns = addOns;
    this.walkIns = walkIns;
    this.checkIn = checkIn;
    this.checkOut = checkOut;
    this.noShows = noShows;
    this.roomChange = roomChange;
    this.folioService = folioService;
    this.kardex = kardex;
    this.walkInService = walkInService;
    this.confirmations = confirmations;
    this.notices = notices;
    this.caller = caller;
  }

  /** What every connected agent is told about this server — the ia-agent's "system-context" prompt. */
  public String systemContext() {
    return """
        Front office del hotel (recepción): las estancias, sus huéspedes y su kárdex, los folios y las habitaciones.
        - Una estancia se identifica por su id del front office (FO-… en los walk-in) o por el localizador del
          CRS; las herramientas aceptan cualquiera de los dos.
        - Lecturas: listArrivals (llegadas de hoy y atrasadas), listDepartures (salidas de hoy), listInHouse,
          getStay, getGuest (con el estado de su kárdex), getFolio, listAvailableRooms, listAddOns,
          getWalkInOffer y quoteWalkIn (precio del CRS; no reserva nada).
        - Operaciones: check-in, check-out, no show, walk-in, cambio de habitación, late check-out y edición del
          kárdex. Ninguna se hace directamente: la herramienta prepare… la comprueba y devuelve un resumen con un
          token. Enséñale el resumen a la persona y pregúntale si lo confirma; solo cuando responda que sí, en su
          siguiente mensaje, llama a confirmAction con ese token. Si dice que no, cancelAction. Nunca confirmes
          en el mismo mensaje en que preparas: el servidor lo rechaza. Si al confirmar no tienes el token,
          listPendingActions da las operaciones pendientes de esa persona.
        - Avisos de recepción (los guarda Salesforce, maestro de clientes): getNotices da los de una estancia
          — de su titular y de sus acompañantes clientes de la cadena — y los cambios de kárdex que Salesforce ha
          rechazado o aún no ha decidido; getStay también los trae. Antes de pedir confirmación de un check-in,
          di SIEMPRE los avisos BLOQUEANTES, con su texto, y pregunta si los ha leído: confirmar el check-in es
          declarar «He leído el aviso» (queda auditado). Antes de un check-out, avisa de los cambios de kárdex
          rechazados (campo, lo propuesto y lo que se queda, y el motivo) o pendientes («la factura saldrá con el
          dato anterior») y de los avisos de salida: confirmar es decir «Entendido».
        - Check-in completo = documento verificado de cada pax (no shows aparte) y el registro firmado (la
          firma se captura en la tablet del mostrador: tú no puedes firmar). Si falta algo, prepareCheckIn lo
          rechaza diciendo qué falta. Si la persona quiere que entre igualmente, prepareForcedCheckIn con un
          MOTIVO obligatorio (pregúntaselo; queda auditado quién, cuándo y por qué): el check-in sube a Opera
          como cualquier otro, pero la estancia queda «Check-in incompleto» y NO puede hacer el check-out
          hasta completarlo (en el front office, «Completar» en la estancia). getStay lo dice (checkIn:
          incomplete, missing, forcedBy, reason, documentsDue, overdue); pasadas 24 h de la llegada sin un
          documento, el parte de viajeros está vencido y recepción recibe un aviso en su bandeja.
        - Un no show de toda la reserva se comunica al CRS; un walk-in reserva en el CRS; los cambios de nombre,
          documento o contacto del titular se proponen al maestro de clientes (Salesforce). Dilo en el resumen.
        """;
  }

  // ── lecturas ───────────────────────────────────────────────────────────────────

  public record StaySummary(String stayId, String crsLocator, String guestName, String status, String room,
                            String roomType, String board, LocalDate checkIn, LocalDate checkOut, int pax,
                            String agency, BigDecimal total, Integer paxPendingIdentity, Boolean readyForCheckIn) {}

  @Tool(description = "Today's arrivals: the stays still to check in whose arrival is today or already past, "
      + "earliest first, with how many pax still lack a verified identity and whether the stay can check in "
      + "with nothing left to ask")
  public List<StaySummary> listArrivals() {
    var today = LocalDate.now();
    return stays.findArrivals().stream()
        .filter(s -> s.status() == StayStatus.ARRIVING && !s.checkIn().isAfter(today))
        .map(this::arrivalSummary).toList();
  }

  @Tool(description = "Today's departures: the stays leaving today, in house or already checked out")
  public List<StaySummary> listDepartures() {
    var today = LocalDate.now();
    return stays.findAll().stream()
        .filter(s -> (s.status() == StayStatus.IN_HOUSE || s.status() == StayStatus.DEPARTED)
            && s.checkOut().isEqual(today))
        .sorted(Comparator.comparing(Stay::status).thenComparing(Stay::id))
        .map(s -> summary(s, null, null)).toList();
  }

  @Tool(description = "The guests in house: every checked-in stay, earliest departure first")
  public List<StaySummary> listInHouse() {
    return stays.findInHouse().stream().map(s -> summary(s, null, null)).toList();
  }

  public record CompanionView(int pax, String name, String document, boolean identityVerified, String email,
                              String phone) {}

  public record IncidentView(String code, String type, String title, String status, boolean complaint) {}

  public record StayDetail(StaySummary stay, String guestId, List<CompanionView> companions,
                           List<String> addOns, List<Integer> noShowPax, Map<String, Boolean> checkInTasks,
                           List<IncidentView> incidents, String vipNote, String walkIn, BigDecimal folioBalance,
                           List<NoticeView> notices, List<KardexWarningView> kardexWarnings,
                           Boolean blockingNoticesRead, Boolean checkOutWarningsRead,
                           CheckInCompleteness checkIn) {}

  /**
   * Where a stay's check-in stands: what it still lacks (documents, signature) and, if it was forced,
   * who forced it, when and why, and when its documents are due (overdue once past).
   */
  public record CheckInCompleteness(boolean complete, boolean incomplete, List<String> missing, boolean forced,
                                    String forcedBy, java.time.Instant forcedAt, String reason,
                                    java.time.Instant documentsDue, boolean overdue, String completedBy) {}

  CheckInCompleteness completeness(Stay stay) {
    var status = incomplete.status(stay);
    var forced = status.forced();
    return new CheckInCompleteness(status.missing().isEmpty(), status.incomplete(),
        status.missing().stream().map(io.mateu.ecdemo1.frontoffice.domain.stay.PendingStep::label).toList(),
        forced != null, forced == null ? null : forced.forcedBy(), forced == null ? null : forced.forcedAt(),
        forced == null ? null : forced.reason(), status.documentsDue(), status.overdue(),
        forced == null ? null : forced.completedBy());
  }

  public record NoticeView(int pax, String guest, String type, String text, List<String> showAt, LocalDate from,
                           LocalDate to) {}

  public record KardexWarningView(int pax, String guest, String status, List<String> detail) {}

  public record StayNotices(String stayId, List<NoticeView> notices, List<KardexWarningView> kardexWarnings,
                            boolean blockingNoticesRead, boolean checkOutWarningsRead) {}

  @Tool(description = "The reception notices (avisos de recepción) of a stay's guests — its holder and the companions "
      + "who are chain customers — as Salesforce keeps them: type (INFORMATIVE, IMPORTANT, BLOCKING), text, where they "
      + "show (CHECK_IN, CHECK_OUT, STAY) and dates; and the kárdex changes Salesforce REJECTED or has PENDING. A "
      + "BLOCKING check-in notice must be read by the person before the check-in; the check-out warnings acknowledged "
      + "before the check-out")
  public StayNotices getNotices(@ToolParam(description = "The stay's front-office id or CRS locator") String stayRef) {
    var stay = stay(stayRef);
    return new StayNotices(stay.id(), noticeViews(notices.activeForStay(stay)), kardexViews(stay),
        notices.checkInAcknowledged(stay), notices.checkOutAcknowledged(stay));
  }

  List<NoticeView> noticeViews(List<GuestNotices.PaxNotice> list) {
    return list.stream().map(p -> new NoticeView(p.pax(), p.guestName(), p.notice().type().name(), p.notice().text(),
        p.notice().showAt().stream().sorted().map(Enum::name).toList(), p.notice().from(), p.notice().to())).toList();
  }

  List<KardexWarningView> kardexViews(Stay stay) {
    return notices.kardexWarnings(stay).stream()
        .map(k -> new KardexWarningView(k.pax(), k.guestName(), k.status().name(), k.lines())).toList();
  }

  @Tool(description = "One stay in full: its status, room, dates, pax and companions, add-ons, the desk's check-in "
      + "tasks, pax marked as no-show, incidents, walk-in state and folio balance; and checkIn: whether its check-in "
      + "is complete, what it still lacks (documents, signature) and — if it was FORCED — who, when, why, when its "
      + "documents are due and whether they are overdue. An incomplete (forced) check-in blocks the check-out")
  public StayDetail getStay(@ToolParam(description = "The stay's front-office id (FO-… for a walk-in) or its CRS locator")
                            String stayRef) {
    var stay = stay(stayRef);
    var ops = queries.ops(stay.id());
    var companions = new ArrayList<CompanionView>();
    for (int i = 0; i < stay.companions().size(); i++) {
      var c = stay.companions().get(i);
      companions.add(new CompanionView(i + 2, c.name(), c.document(), c.identityComplete(), c.email(), c.phone()));
    }
    var tasks = new LinkedHashMap<String, Boolean>();
    tasks.put("wifi", ops.wifi());
    tasks.put("llave", ops.llave());
    tasks.put("firma", ops.firma());
    tasks.put("cobro", ops.cobro());
    tasks.put("extras", ops.extras());
    var folio = folios.findByStayId(stay.id());
    return new StayDetail(
        stay.status() == StayStatus.ARRIVING ? arrivalSummary(stay) : summary(stay, null, null),
        stay.guestId(), companions,
        stay.addOns().stream().map(a -> addOns.findById(a.addOnId()).map(AddOnCatalogItem::title).orElse(a.addOnId()))
            .sorted().toList(),
        ops.noShowPax() == null ? List.of() : ops.noShowPax().stream().sorted().toList(), tasks,
        stay.incidents().stream().map(i -> new IncidentView(i.code(), i.type() == null ? null : i.type().name(),
            i.title(), i.status() == null ? null : i.status().name(), i.complaint())).toList(),
        stay.vipNote(), walkIns.of(stay.id()).map(WalkIn::label).orElse(null),
        folio.map(Folio::balance).orElse(null), noticeViews(notices.activeForStay(stay)), kardexViews(stay),
        notices.checkInAcknowledged(stay), notices.checkOutAcknowledged(stay), completeness(stay));
  }

  public record FieldChangeView(String field, String before, String after) {}

  public record KardexView(String status, List<FieldChangeView> fields, String reason, boolean sentToMaster) {}

  public record GuestView(String guestId, String name, String document, boolean identityVerified, String email,
                          String phone, String tier, int loyaltyPoints, int stays, int nights, int complaints,
                          List<String> preferences, String lastStay, KardexView kardex) {}

  @Tool(description = "A guest's kárdex: identity, contact, loyalty, preferences — and the state of the desk's last "
      + "change to it, pending or decided by the chain's customer master (Salesforce)")
  public GuestView getGuest(@ToolParam(description = "A stay's id or CRS locator (its holder), or the guest's id")
                            String ref) {
    var guest = guests.findById(ref == null ? "" : ref)
        .orElseGet(() -> guests.findById(stay(ref).guestId())
            .orElseThrow(() -> new NoSuchElementException("No guest for " + ref)));
    var change = kardex.changeOf(guest.id())
        .map(c -> new KardexView(c.status().name(),
            c.fields().stream().map(f -> new FieldChangeView(f.label(), f.before(), f.after())).toList(),
            c.reason(), c.synced()))
        .orElse(null);
    return new GuestView(guest.id(), guest.name(), guest.document(), guest.identityComplete(), guest.email(),
        guest.phone(), guest.tier() == null ? null : guest.tier().name(), guest.loyaltyPoints(), guest.stays(),
        guest.nights(), guest.complaints(), guest.preferences().stream().map(p -> p.text()).toList(),
        guest.lastStaySummary(), change);
  }

  public record FolioLineView(String concept, BigDecimal amount, boolean included) {}

  public record FolioView(String stayId, String folioId, BigDecimal preauthorized, BigDecimal balance,
                          boolean lateCheckOutContracted, List<FolioLineView> lines) {}

  @Tool(description = "A stay's folio: its charges, balance, the card pre-authorization and whether late check-out "
      + "is contracted. A stay that has not checked in has no folio yet")
  public FolioView getFolio(@ToolParam(description = "The stay's front-office id or CRS locator") String stayRef) {
    var stay = stay(stayRef);
    var folio = folios.findByStayId(stay.id()).orElse(null);
    if (folio == null) {
      return new FolioView(stay.id(), null, null, BigDecimal.ZERO, false, List.of());
    }
    return new FolioView(stay.id(), folio.id(), folio.preauthorized(), folio.balance(), folio.lateCheckOutContracted(),
        folio.lines().stream().map(l -> new FolioLineView(l.concept(), l.amount(), l.included())).toList());
  }

  public record RoomView(String number, int floor, String type, String housekeeping, String maintenanceNote) {}

  @Tool(description = "The rooms free to assign now, with their housekeeping state (DIRTY, CLEAN, INSPECTED) and any "
      + "open maintenance note")
  public List<RoomView> listAvailableRooms(
      @ToolParam(description = "Only rooms whose type contains this text; empty for all", required = false)
      String type) {
    return rooms.findAll().stream().filter(Room::assignable)
        .filter(r -> type == null || type.isBlank()
            || (r.typeLabel() != null && r.typeLabel().toLowerCase().contains(type.toLowerCase())))
        .sorted(Comparator.comparing(Room::number))
        .map(r -> new RoomView(r.number(), r.floor(), r.typeLabel(),
            r.housekeeping() == null ? null : r.housekeeping().name(), r.maintenanceNote()))
        .toList();
  }

  public record AddOnView(String id, String title, BigDecimal price, String unit, String includedLabel) {}

  @Tool(description = "The add-ons a stay can contract at check-in (their ids go in prepareCheckIn)")
  public List<AddOnView> listAddOns() {
    return addOns.findAll().stream()
        .map(a -> new AddOnView(a.id(), a.title(), a.price(), a.unit(), a.includedLabel())).toList();
  }

  @Tool(description = "What the CRS sells at this hotel for a walk-in: its room types, rate plans and boards, by code")
  public WalkInDesk.Offer getWalkInOffer() {
    return walkInService.offer();
  }

  @Tool(description = "The CRS's price for a walk-in stay. Only a quote: nothing is booked")
  public WalkInDesk.Quote quoteWalkIn(
      @ToolParam(description = "Arrival date, ISO (yyyy-MM-dd); usually today") LocalDate arrival,
      @ToolParam(description = "Departure date, ISO") LocalDate departure,
      @ToolParam(description = "CRS room type code, from getWalkInOffer") String roomTypeCode,
      @ToolParam(description = "CRS rate plan code, from getWalkInOffer") String ratePlanCode,
      @ToolParam(description = "CRS board code, from getWalkInOffer") String boardCode,
      @ToolParam(description = "Adults") int adults,
      @ToolParam(description = "The children's ages; empty if none", required = false) List<Integer> childrenAges) {
    return walkInService.quote(new WalkInDesk.Request(null, null, arrival, departure, roomTypeCode, ratePlanCode,
        boardCode, adults, childrenAges == null ? List.of() : childrenAges, null, null));
  }

  // ── operaciones: se preparan aquí y se hacen con confirmAction ─────────────────

  @Tool(description = "Prepare the check-in of an arriving stay: the room (the reservation's if none is given) and the "
      + "add-ons it contracts. Every pax must have a verified identity (register them with prepareKardexEdit first). "
      + "Nothing is done until the person confirms (confirmAction)")
  public String prepareCheckIn(
      @ToolParam(description = "The stay's front-office id or CRS locator") String stayRef,
      @ToolParam(description = "Room number to assign; empty to keep the reservation's", required = false)
      String roomNumber,
      @ToolParam(description = "Add-on ids to contract, from listAddOns; empty for none", required = false)
      List<String> addOnIds) {
    var stay = stay(stayRef);
    if (stay.status() != StayStatus.ARRIVING) {
      return refuse("la estancia " + stay.id() + " no está pendiente de llegada (" + stay.status() + ").");
    }
    var pending = queries.pendingPax(stay);
    if (pending > 0) {
      return refuse(pending + " pax de " + stay.id() + " sin identidad verificada: regístralos antes con "
          + "prepareKardexEdit, o que los escaneen en el mostrador. Si tiene que entrar igualmente, "
          + "prepareForcedCheckIn con el motivo.");
    }
    var missing = incomplete.missing(stay);
    if (!missing.isEmpty()) {
      return refuse("al check-in de " + stay.id() + " le falta: " + String.join("; ", missing.stream()
          .map(io.mateu.ecdemo1.frontoffice.domain.stay.PendingStep::label).toList()) + " (la firma, en la tablet "
          + "del mostrador). Si tiene que entrar igualmente, prepareForcedCheckIn con el motivo.");
    }
    var number = roomNumber == null || roomNumber.isBlank() ? stay.roomNumber() : roomNumber.trim();
    if (number == null || number.isBlank()) {
      return refuse("la estancia no tiene habitación: indica una libre (listAvailableRooms).");
    }
    var room = rooms.findByNumber(number).orElse(null);
    if (room == null) {
      return refuse("no existe la habitación " + number + ".");
    }
    if (!room.assignable() && !number.equals(stay.roomNumber())) {
      return refuse("la habitación " + number + " no está libre.");
    }
    var chosen = addOnIds == null ? List.<String>of() : addOnIds.stream().filter(Objects::nonNull).map(String::trim)
        .filter(id -> !id.isBlank()).distinct().toList();
    var titles = new ArrayList<String>();
    for (var id : chosen) {
      var item = addOns.findById(id).orElse(null);
      if (item == null) {
        return refuse("no hay ningún extra con id " + id + " (listAddOns).");
      }
      titles.add(item.title() + (item.price() == null ? "" : " (" + item.price() + " €)"));
    }
    var guest = guests.findById(stay.guestId()).map(Guest::name).orElse(stay.guestId());
    var summary = "Check-in de %s (%s), %d pax, del %s al %s, en la habitación %s%s. Se abre su folio con el alojamiento (%s €)%s."
        .formatted(stay.id(), guest, stay.pax(), stay.checkIn(), stay.checkOut(), number,
            room.housekeeping() == null ? "" : " (" + room.housekeeping() + ")", stay.total(),
            titles.isEmpty() ? "" : " y los extras: " + String.join(", ", titles));
    // Los avisos del check-in, en el resumen; un bloqueante sin leer se declara leído al confirmar.
    var avisos = notices.forStay(stay, CustomerNotice.Moment.CHECK_IN);
    var mustRead = !notices.checkInAcknowledged(stay);
    var fingerprint = notices.checkInFingerprint(stay);
    if (!avisos.isEmpty()) {
      summary += " Avisos de recepción: " + String.join(" ", avisos.stream().map(p -> (p.notice().blocking()
          ? "AVISO BLOQUEANTE" : "Aviso " + p.notice().typeLabel().toLowerCase()) + " de " + p.guestName() + ": «"
          + p.notice().text() + "».").toList());
      if (mustRead) {
        summary += " Al confirmar, la persona declara que ha leído el aviso bloqueante (queda auditado).";
      }
    }
    var params = params("stayId", stay.id(), "roomNumber", number, "addOnIds", chosen, "blockingNotices", fingerprint);
    var stayId = stay.id();
    return confirmations.prepare("Check-in", summary, params, () -> {
      var by = PendingConfirmations.actor(caller.person());
      if (mustRead) {
        notices.acknowledgeCheckIn(stayId, by, fingerprint);
      }
      var done = checkIn.checkIn(stayId, number, chosen, by);
      if (done.status() != StayStatus.IN_HOUSE) {
        throw new IllegalStateException("la estancia ya no estaba pendiente de llegada (" + done.status() + ")");
      }
      return "Check-in de " + stayId + " hecho: en casa, habitación " + done.roomNumber() + ".";
    });
  }

  @Tool(description = "Prepare the check-in of an arriving stay FORCED with steps missing (a pax's document, the "
      + "registration's signature): only when the person says the guest must go in anyway, and with the REASON they "
      + "give (mandatory, audited: who, when, why). It goes up to Opera like any check-in; the stay is left «Check-in "
      + "incompleto» and cannot check out until it is completed at the desk («Completar»). A blocking reception "
      + "notice must still be read. Nothing is done until the person confirms (confirmAction)")
  public String prepareForcedCheckIn(
      @ToolParam(description = "The stay's front-office id or CRS locator") String stayRef,
      @ToolParam(description = "Why it goes in with steps missing — the person's words; mandatory") String reason,
      @ToolParam(description = "Room number to assign; empty to keep the reservation's", required = false)
      String roomNumber,
      @ToolParam(description = "Add-on ids to contract, from listAddOns; empty for none", required = false)
      List<String> addOnIds) {
    var stay = stay(stayRef);
    if (stay.status() != StayStatus.ARRIVING) {
      return refuse("la estancia " + stay.id() + " no está pendiente de llegada (" + stay.status() + ").");
    }
    if (reason == null || reason.isBlank()) {
      return refuse("forzar el check-in necesita un motivo: pregúntaselo a la persona.");
    }
    var missing = incomplete.missing(stay);
    if (missing.isEmpty()) {
      return refuse("al check-in de " + stay.id() + " no le falta nada: usa prepareCheckIn.");
    }
    var number = roomNumber == null || roomNumber.isBlank() ? stay.roomNumber() : roomNumber.trim();
    if (number == null || number.isBlank()) {
      return refuse("la estancia no tiene habitación: indica una libre (listAvailableRooms).");
    }
    var room = rooms.findByNumber(number).orElse(null);
    if (room == null) {
      return refuse("no existe la habitación " + number + ".");
    }
    if (!room.assignable() && !number.equals(stay.roomNumber())) {
      return refuse("la habitación " + number + " no está libre.");
    }
    var chosen = addOnIds == null ? List.<String>of() : addOnIds.stream().filter(Objects::nonNull).map(String::trim)
        .filter(id -> !id.isBlank()).distinct().toList();
    for (var id : chosen) {
      if (addOns.findById(id).isEmpty()) {
        return refuse("no hay ningún extra con id " + id + " (listAddOns).");
      }
    }
    var falta = String.join("; ", missing.stream().map(io.mateu.ecdemo1.frontoffice.domain.stay.PendingStep::label)
        .toList());
    var guest = guests.findById(stay.guestId()).map(Guest::name).orElse(stay.guestId());
    var summary = ("Check-in FORZADO de %s (%s), %d pax, en la habitación %s, con pasos pendientes: %s. Motivo: «%s». "
        + "Sube a Opera como cualquier check-in; la estancia queda «Check-in incompleto» y no podrá hacer el "
        + "check-out hasta completarlo; si falta un documento pasadas %s, el parte de viajeros vence y recepción "
        + "recibe un aviso. Queda auditado quién, cuándo y por qué.").formatted(stay.id(), guest, stay.pax(), number,
        falta, reason.trim(), incomplete.documentDeadlineLabel());
    var mustRead = !notices.checkInAcknowledged(stay);
    var fingerprint = notices.checkInFingerprint(stay);
    var blocking = notices.blockingAtCheckIn(stay);
    if (!blocking.isEmpty() && mustRead) {
      summary += " AVISO BLOQUEANTE: " + String.join(" ", blocking.stream()
          .map(p -> "de " + p.guestName() + ": «" + p.notice().text() + "».").toList())
          + " Al confirmar, la persona declara que lo ha leído (queda auditado).";
    }
    var params = params("stayId", stay.id(), "roomNumber", number, "addOnIds", chosen, "reason", reason.trim(),
        "missing", falta, "blockingNotices", fingerprint);
    var stayId = stay.id();
    var motivo = reason.trim();
    return confirmations.prepare("Forced check-in", summary, params, () -> {
      var by = PendingConfirmations.actor(caller.person());
      if (mustRead) {
        notices.acknowledgeCheckIn(stayId, by, fingerprint);
      }
      var done = checkIn.forceCheckIn(stayId, number, chosen, motivo, by);
      if (done.status() != StayStatus.IN_HOUSE) {
        throw new IllegalStateException("la estancia ya no estaba pendiente de llegada (" + done.status() + ")");
      }
      return "Check-in forzado de " + stayId + " hecho: en casa, habitación " + done.roomNumber()
          + ". Check-in incompleto: falta " + falta + ".";
    });
  }

  @Tool(description = "Prepare the check-out of an in-house stay: it leaves and its room is freed to be cleaned. "
      + "Refused while its check-in, forced, is incomplete (a document or the signature missing: completed first at "
      + "the desk). Nothing is done until the person confirms (confirmAction)")
  public String prepareCheckOut(@ToolParam(description = "The stay's front-office id or CRS locator") String stayRef) {
    var stay = stay(stayRef);
    if (!stay.inHouse()) {
      return refuse("la estancia " + stay.id() + " no está en casa (" + stay.status() + ").");
    }
    var checkInStatus = incomplete.status(stay);
    if (checkInStatus.incomplete()) {
      return refuse("el check-in de " + stay.id() + " se forzó y sigue incompleto: falta " + checkInStatus.missingText()
          + (checkInStatus.overdue() ? " (el parte de viajeros ya está vencido)" : "")
          + ". No puede salir hasta completarlo en el front office («Completar» en la estancia).");
    }
    var balance = folios.findByStayId(stay.id()).map(Folio::balance).orElse(BigDecimal.ZERO);
    var summary = "Check-out de %s, habitación %s (salida prevista %s). Saldo del folio: %s €. La habitación queda libre y sucia."
        .formatted(stay.id(), stay.roomNumber(), stay.checkOut(), balance);
    // Lo que la salida advierte: kárdex rechazado o pendiente en Salesforce, avisos de check-out.
    var warnings = notices.checkOutWarnings(stay);
    var mustAcknowledge = warnings.any() && !notices.checkOutAcknowledged(stay);
    for (var k : warnings.kardex()) {
      summary += " ATENCIÓN, kárdex de " + k.guestName() + (k.rejected() ? " RECHAZADO por Salesforce: "
          : " PENDIENTE de Salesforce (la factura saldrá con el dato anterior): ") + String.join("; ", k.lines()) + ".";
    }
    for (var p : warnings.notices()) {
      summary += " Aviso " + p.notice().typeLabel().toLowerCase() + " de " + p.guestName() + ": «" + p.notice().text() + "».";
    }
    if (mustAcknowledge) {
      summary += " Al confirmar, la persona dice «Entendido» a estos avisos (queda auditado).";
    }
    var stayId = stay.id();
    var fingerprint = warnings.fingerprint();
    return confirmations.prepare("Check-out", summary, params("stayId", stayId, "warnings", fingerprint), () -> {
      var by = PendingConfirmations.actor(caller.person());
      if (mustAcknowledge) {
        notices.acknowledgeCheckOut(stayId, by, fingerprint);
      }
      var done = checkOut.checkOut(stayId, by);
      if (done.status() != StayStatus.DEPARTED) {
        throw new IllegalStateException("la estancia ya no estaba en casa (" + done.status() + ")");
      }
      return "Check-out de " + stayId + " hecho.";
    });
  }

  @Tool(description = "Prepare marking one pax of an arriving stay as a no-show (or taking the mark back). When that "
      + "leaves nobody of the reservation arriving, the whole reservation is a no-show and the CRS is told. Nothing is "
      + "done until the person confirms (confirmAction)")
  public String prepareNoShow(
      @ToolParam(description = "The stay's front-office id or CRS locator") String stayRef,
      @ToolParam(description = "The pax: 1 is the holder, 2… the companions") int pax,
      @ToolParam(description = "true to mark the pax as not arrived; false to take the mark back") boolean noShow) {
    var stay = stay(stayRef);
    if (stay.status() != StayStatus.ARRIVING) {
      return refuse("la estancia " + stay.id() + " no está pendiente de llegada (" + stay.status() + ").");
    }
    if (pax < 1 || pax > stay.pax()) {
      return refuse("la estancia tiene " + stay.pax() + " pax; el " + pax + " no existe.");
    }
    var ops = queries.ops(stay.id());
    if (ops.isNoShow(pax) == noShow) {
      return refuse("el pax " + pax + (noShow ? " ya está marcado como no show." : " no está marcado como no show."));
    }
    var whole = noShow && CheckInChecklist.nobodyArrived(stay, ops.toggleNoShow(pax));
    var summary = (noShow ? "Marcar el pax %d de %s como no show." : "Quitar la marca de no show del pax %d de %s.")
        .formatted(pax, stay.id())
        + (whole ? " Con esto no llega nadie de la reserva: es un NO SHOW DE TODA LA RESERVA y se comunica al CRS, "
            + "que la cancela con su penalización." : "");
    var stayId = stay.id();
    return confirmations.prepare("No show", summary, params("stayId", stayId, "pax", pax, "noShow", noShow), () -> {
      if (queries.ops(stayId).isNoShow(pax) != !noShow) {
        throw new IllegalStateException("el pax " + pax + " ya había cambiado");
      }
      var outcome = noShows.paxToggled(stayId, pax);
      return (outcome.noShow() ? "Pax " + pax + " marcado como no show." : "Pax " + pax + " ya no es no show.")
          + (outcome.nobodyArrived() ? " No show de toda la reserva; el CRS responde: " + outcome.crsNotice() : "");
    });
  }

  @Tool(description = "Prepare a walk-in: a guest with no reservation. The CRS is asked for the price now, and the "
      + "summary says it; confirmed, the stay opens here (to check in at once) and the booking is sent to the CRS at "
      + "that price. Holder's first name, last name and document are required. Nothing is done until the person "
      + "confirms (confirmAction)")
  public String prepareWalkIn(
      @ToolParam(description = "Arrival date, ISO (yyyy-MM-dd); usually today") LocalDate arrival,
      @ToolParam(description = "Departure date, ISO") LocalDate departure,
      @ToolParam(description = "CRS room type code, from getWalkInOffer") String roomTypeCode,
      @ToolParam(description = "CRS rate plan code, from getWalkInOffer") String ratePlanCode,
      @ToolParam(description = "CRS board code, from getWalkInOffer") String boardCode,
      @ToolParam(description = "Adults") int adults,
      @ToolParam(description = "The children's ages; empty if none", required = false) List<Integer> childrenAges,
      @ToolParam(description = "Holder's first name") String firstName,
      @ToolParam(description = "Holder's last name(s)") String lastName,
      @ToolParam(description = "Document type: PASSPORT or ID_CARD", required = false) String documentType,
      @ToolParam(description = "Document number") String documentNumber,
      @ToolParam(description = "Holder's e-mail", required = false) String email,
      @ToolParam(description = "Holder's phone", required = false) String phone,
      @ToolParam(description = "Nationality, ISO country code (ES, DE…)", required = false) String nationality) {
    var holder = new WalkInDesk.Holder(trim(firstName), trim(lastName), trim(email), trim(phone),
        nationality == null ? null : nationality.trim().toUpperCase(),
        documentType == null || documentType.isBlank() ? "PASSPORT" : documentType.trim().toUpperCase(),
        trim(documentNumber));
    var missing = WalkInService.missing(holder);
    if (!missing.isEmpty()) {
      return refuse("falta del titular: " + String.join(", ", missing) + ".");
    }
    var ages = childrenAges == null ? List.<Integer>of() : List.copyOf(childrenAges);
    var request = new WalkInDesk.Request(null, null, arrival, departure, roomTypeCode, ratePlanCode, boardCode, adults,
        ages, holder, null);
    WalkInDesk.Quote quote;
    try {
      quote = walkInService.quote(request);
    } catch (RuntimeException e) {
      return refuse(e.getMessage());
    }
    var offer = walkInService.offer();
    var summary = ("Walk-in de %s (%s %s), %d adulto(s)%s, del %s al %s (%d noche(s)): %s, %s, %s. Precio del CRS: %s %s. "
        + "Se abre la estancia en el front office y se envía la reserva al CRS a ese precio.")
        .formatted(holder.fullName().trim(), holder.documentType(), holder.documentNumber(), adults,
            ages.isEmpty() ? "" : " y " + ages.size() + " niño(s)", arrival, departure, quote.nights(),
            offer.name(offer.roomTypes(), roomTypeCode), offer.name(offer.ratePlans(), ratePlanCode),
            offer.name(offer.boards(), boardCode), quote.total(), quote.currency());
    var params = params("arrival", arrival, "departure", departure, "roomTypeCode", roomTypeCode, "ratePlanCode",
        ratePlanCode, "boardCode", boardCode, "adults", adults, "childrenAges", ages, "holder", holder.fullName().trim(),
        "quotedTotal", quote.total());
    var total = quote.total();
    return confirmations.prepare("Walk-in", summary, params, () -> {
      var walkIn = walkInService.confirm(new WalkInDesk.Request(null, null, arrival, departure, roomTypeCode,
          ratePlanCode, boardCode, adults, ages, holder, total), total);
      return "Estancia " + walkIn.stayId() + " abierta; " + walkIn.label() + ".";
    });
  }

  @Tool(description = "Prepare moving a stay to another room: assigned before arrival, or — for a guest in house — a "
      + "move that frees the old room. The new room must be free. Nothing is done until the person confirms "
      + "(confirmAction)")
  public String prepareRoomChange(
      @ToolParam(description = "The stay's front-office id or CRS locator") String stayRef,
      @ToolParam(description = "The new room's number") String roomNumber) {
    var stay = stay(stayRef);
    if (!stay.occupies()) {
      return refuse("la estancia " + stay.id() + " no espera habitación (" + stay.status() + ").");
    }
    var number = trim(roomNumber);
    var room = number == null ? null : rooms.findByNumber(number).orElse(null);
    if (room == null) {
      return refuse("no existe la habitación " + roomNumber + ".");
    }
    if (!room.assignable()) {
      return refuse("la habitación " + number + " no está libre.");
    }
    var summary = (stay.inHouse()
        ? "Mover a %s (en casa) de la habitación %s a la %s (%s, %s); la %s queda libre y sucia."
            .formatted(stay.id(), stay.roomNumber(), number, room.typeLabel(), room.housekeeping(), stay.roomNumber())
        : "Asignar a %s (llega el %s) la habitación %s (%s, %s)%s."
            .formatted(stay.id(), stay.checkIn(), number, room.typeLabel(), room.housekeeping(),
                stay.hasRoom() ? " en lugar de la " + stay.roomNumber() : ""));
    var stayId = stay.id();
    return confirmations.prepare("Room change", summary, params("stayId", stayId, "from", stay.roomNumber(),
        "to", number), () -> roomChange.changeRoom(stayId, number)
        .map(r -> stayId + " está ahora en la habitación " + r.number() + ".")
        .orElseThrow(() -> new IllegalStateException("la habitación " + number + " ya no está libre")));
  }

  @Tool(description = "Prepare a late check-out for an in-house stay: leaving at 15:00 instead of 12:00, charged to "
      + "its folio once. Nothing is done until the person confirms (confirmAction)")
  public String prepareLateCheckOut(@ToolParam(description = "The stay's front-office id or CRS locator") String stayRef) {
    var stay = stay(stayRef);
    if (!stay.inHouse()) {
      return refuse("la estancia " + stay.id() + " no está en casa (" + stay.status() + ").");
    }
    if (folios.findByStayId(stay.id()).map(Folio::lateCheckOutContracted).orElse(false)) {
      return refuse(stay.id() + " ya tiene el late check-out contratado.");
    }
    var summary = "Late check-out para %s, habitación %s: salida el %s a las 15:00 en vez de a las 12:00. Se carga en su folio: %s €."
        .formatted(stay.id(), stay.roomNumber(), stay.checkOut(), Folio.LATE_CHECK_OUT_FEE);
    var stayId = stay.id();
    return confirmations.prepare("Late check-out", summary, params("stayId", stayId, "fee", Folio.LATE_CHECK_OUT_FEE),
        () -> {
          if (!folioService.contractLateCheckOut(stayId, "agente de recepción")) {
            throw new IllegalStateException("ya estaba contratado");
          }
          return "Late check-out de " + stayId + " contratado; " + Folio.LATE_CHECK_OUT_FEE + " € cargados en su folio.";
        });
  }

  @Tool(description = "Prepare registering or correcting a pax's kárdex at the desk: document, name and contact. It "
      + "marks the pax's identity as seen. What is not given stays as it is. For the holder (pax 1), a change of name, "
      + "document or contact is proposed to the chain's customer master (Salesforce), which decides it. Nothing is done "
      + "until the person confirms (confirmAction)")
  public String prepareKardexEdit(
      @ToolParam(description = "The stay's front-office id or CRS locator") String stayRef,
      @ToolParam(description = "The pax: 1 is the holder, 2… the companions") int pax,
      @ToolParam(description = "Document number; empty to keep it", required = false) String document,
      @ToolParam(description = "Full name; empty to keep it", required = false) String name,
      @ToolParam(description = "E-mail; empty to keep it", required = false) String email,
      @ToolParam(description = "Phone; empty to keep it", required = false) String phone) {
    var stay = stay(stayRef);
    if (pax < 1 || pax > stay.pax()) {
      return refuse("la estancia tiene " + stay.pax() + " pax; el " + pax + " no existe.");
    }
    String curDocument, curName, curEmail, curPhone;
    if (pax == 1) {
      var guest = guests.findById(stay.guestId()).orElseThrow(() -> new NoSuchElementException("No guest " + stay.guestId()));
      curDocument = guest.document();
      curName = guest.name();
      curEmail = guest.email();
      curPhone = guest.phone();
    } else {
      var companion = stay.companionAt(pax);
      curDocument = companion == null ? null : companion.document();
      curName = companion == null ? null : companion.name();
      curEmail = companion == null ? null : companion.email();
      curPhone = companion == null ? null : companion.phone();
    }
    var newDocument = or(trim(document), curDocument);
    var newName = or(trim(name), curName);
    var newEmail = or(trim(email), curEmail);
    var newPhone = or(trim(phone), curPhone);
    var changes = new ArrayList<String>();
    change(changes, "documento", curDocument, newDocument);
    change(changes, "nombre", curName, newName);
    change(changes, "email", curEmail, newEmail);
    change(changes, "teléfono", curPhone, newPhone);
    var summary = "Kárdex del pax %d de %s%s: %s. La identidad queda como vista en el mostrador%s."
        .formatted(pax, stay.id(), curName == null ? "" : " (" + curName + ")",
            changes.isEmpty() ? "sin cambios de datos" : String.join("; ", changes),
            pax == 1 && !changes.isEmpty() ? "; el cambio se propone al maestro de clientes (Salesforce), que lo decide" : "");
    var stayId = stay.id();
    return confirmations.prepare("Kardex edit", summary, params("stayId", stayId, "pax", pax, "changes", changes),
        () -> {
          kardex.registered(stayId, pax, newDocument, newName, newEmail, newPhone);
          return "Kárdex del pax " + pax + " de " + stayId + " registrado"
              + (pax == 1 && !changes.isEmpty() ? "; cambio enviado al maestro de clientes." : ".");
        });
  }

  @Tool(description = "Carry out an operation a prepare… tool prepared — ONLY after the person has read its summary and "
      + "said yes, in a later message. Refused if called in the same message the operation was prepared in. "
      + "Without the token at hand, take it from listPendingActions — never make one up")
  public String confirmAction(@ToolParam(description = "The token the prepare… tool returned") String token) {
    return confirmations.confirm(token);
  }

  @Tool(description = "The operations prepared for the person calling that still wait for their yes, newest first, "
      + "with their tokens and summaries. Use it to find the token when the person confirms")
  public List<PendingConfirmations.PendingView> listPendingActions() {
    return confirmations.pendingForCaller();
  }

  @Tool(description = "Drop an operation a prepare… tool prepared, when the person does not want it")
  public String cancelAction(@ToolParam(description = "The token the prepare… tool returned") String token) {
    return confirmations.cancel(token);
  }

  // ── ─────────────────────────────────────────────────────────────────────────────

  /** A stay by its front-office id, or by the CRS locator it (or the walk-in it came from) carries. */
  Stay stay(String ref) {
    var key = ref == null ? "" : ref.trim();
    return stays.findById(key)
        .or(() -> walkIns.byLocator(key).flatMap(w -> stays.findById(w.stayId())))
        .or(() -> key.equals(key.toUpperCase()) ? java.util.Optional.empty() : stays.findById(key.toUpperCase()))
        .orElseThrow(() -> new NoSuchElementException("No hay ninguna estancia " + ref));
  }

  StaySummary arrivalSummary(Stay s) {
    return summary(s, queries.pendingPax(s), queries.readyForDirectCheckIn(s));
  }

  StaySummary summary(Stay s, Integer pendingPax, Boolean ready) {
    var guest = guests.findById(s.guestId()).map(Guest::name).orElse(null);
    var locator = walkIns.of(s.id()).map(WalkIn::locator).orElse(s.id().startsWith("FO-") ? null : s.id());
    return new StaySummary(s.id(), locator, guest, s.status().name(), s.hasRoom() ? s.roomNumber() : null,
        s.roomType(), s.board(), s.checkIn(), s.checkOut(), s.pax(), s.agency(), s.total(), pendingPax, ready);
  }

  static String refuse(String why) {
    return "No se puede preparar: " + why;
  }

  static Map<String, Object> params(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }

  static void change(List<String> changes, String field, String before, String after) {
    if (!Objects.equals(blankToNull(before), blankToNull(after))) {
      changes.add(field + ": " + (before == null ? "—" : before) + " → " + after);
    }
  }

  static String or(String value, String current) {
    return value == null ? current : value;
  }

  static String trim(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
