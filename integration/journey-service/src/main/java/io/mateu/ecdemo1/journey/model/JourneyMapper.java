package io.mateu.ecdemo1.journey.model;

import io.mateu.ecdemo1.journey.tempo.TraceSpan;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A trace, as the journey of one change of a booking: which spans are this booking's, what each
 * one means in business words, and what the business asks of the whole — the time from the CRS to
 * Opera and to the front office, where it stands, what it waited on.
 *
 * <p>Pure: spans in, a {@link Journey} out. The infrastructure — outboxes, Kafka hand-offs, the
 * engine's bookkeeping, reads that change nothing — is left out; what it cost shows as the gaps
 * between hops. What the spans say about the business comes from the attributes the services put
 * on them ({@code booking.event}, {@code opera.reservation.id}, {@code mdm.identities}, {@code
 * mapping.translations} …) and, for traces written before they did, from the URLs they called.
 */
public final class JourneyMapper {

    static final Pattern OPERA_RESERVATION = Pattern.compile("/(?:rsv|fof|csh)/v1/hotels/([^/?]+)/reservations/([^/?]+)");
    static final Pattern OPERA_RESERVATIONS = Pattern.compile("/rsv/v1/hotels/([^/?]+)/reservations");
    static final Pattern OPERA_PROFILE = Pattern.compile("/crm/v1/profiles/([^/?]+)");
    static final Pattern OPERA_XREF = Pattern.compile("xref=OPERA:([^&]+)");
    static final Pattern MDM_CUSTOMER = Pattern.compile("/customers/(C-[A-Z0-9]+)");
    static final Pattern WRITTEN_VERSION = Pattern.compile("v(\\d+) is in Opera");
    static final Pattern STAY_KEY = Pattern.compile("^proyectar-estancia:([^:]+):([^:]+):");

    /** A trace this young that has not reached Opera may still be arriving: Tempo ingests in seconds. */
    static final Duration SETTLING = Duration.ofSeconds(30);

    private JourneyMapper() {
    }

    public static Journey map(String traceId, String locator, List<TraceSpan> all, long nowNanos) {
        var spans = relevant(all, locator);
        var facts = new Facts(locator, spans);
        var hops = new ArrayList<Hop>();
        for (var span : spans) {
            facts.hop(span).ifPresent(hops::add);
        }
        hops.sort(Comparator.comparingLong(Hop::startNanos));
        return facts.journey(traceId, hops, nowNanos);
    }

    // ── which spans are this booking's ───────────────────────────────────────────────────────────

    /**
     * The spans of this booking. Most traces are one booking's; an approval that resolves a cause
     * resumes every reservation waiting on it, in one trace, and then only this booking's part is
     * wanted: the spans that name it, its processes' spans, everything under them and the path
     * from them up to the root.
     */
    static List<TraceSpan> relevant(List<TraceSpan> all, String locator) {
        var byId = new HashMap<String, TraceSpan>();
        var children = new HashMap<String, List<TraceSpan>>();
        for (var span : all) {
            byId.put(span.spanId(), span);
        }
        for (var span : all) {
            if (span.parentId() != null && byId.containsKey(span.parentId())) {
                children.computeIfAbsent(span.parentId(), k -> new ArrayList<>()).add(span);
            }
        }
        var processes = new HashSet<String>();
        for (var span : all) {
            if (mentions(span, locator) && span.has("eventconductor.process.id")) {
                processes.add(span.attr("eventconductor.process.id"));
            }
        }
        var anchors = all.stream()
                .filter(s -> mentions(s, locator) || processes.contains(s.attr("eventconductor.process.id")))
                .toList();
        if (anchors.isEmpty()) {
            return sorted(all);
        }
        var keep = new HashSet<String>();
        var queue = new ArrayDeque<TraceSpan>(anchors);
        while (!queue.isEmpty()) {
            var span = queue.poll();
            if (keep.add(span.spanId())) {
                queue.addAll(children.getOrDefault(span.spanId(), List.of()));
            }
        }
        for (var anchor : anchors) {
            var parent = byId.get(anchor.parentId());
            while (parent != null && keep.add(parent.spanId())) {
                parent = byId.get(parent.parentId());
            }
        }
        return sorted(all.stream().filter(s -> keep.contains(s.spanId())).toList());
    }

    static boolean mentions(TraceSpan span, String locator) {
        if (locator.equals(span.attr("booking.locator")) || locator.equals(span.attr("booking.id"))) {
            return true;
        }
        for (var key : new String[]{span.attr("eventconductor.process.businessKey"), span.attr("eventconductor.business-key")}) {
            if (key != null && (key.contains("/" + locator + ":") || key.endsWith("/" + locator) || key.contains("/" + locator + ">"))) {
                return true;
            }
        }
        return false;
    }

    static List<TraceSpan> sorted(List<TraceSpan> spans) {
        return spans.stream().sorted(Comparator.comparingLong(TraceSpan::startNanos)).toList();
    }

    // ── what the spans mean ──────────────────────────────────────────────────────────────────────

    /** What is learned from the spans while they are read, and the hops they become. */
    static final class Facts {

        final String locator;
        final List<TraceSpan> spans;
        final Map<String, TraceSpan> byId = new HashMap<>();
        final Map<String, List<TraceSpan>> children = new HashMap<>();
        /** The worker's span of each step execution, its attributes merged when it was reported twice. */
        final Map<String, Map<String, String>> taskAttributes = new HashMap<>();
        final Map<String, TraceSpan> taskSpans = new HashMap<>();
        final boolean walkIn;

        String hotel;
        String event;
        Long version;
        String operaHotel;
        String operaReservationId;
        String operaAction;
        long operaWrittenAt;
        long frontOfficeAt;
        final Map<String, ProcessRef> processes = new LinkedHashMap<>();
        final List<String> translations = new ArrayList<>();
        final List<String> causes = new ArrayList<>();
        final List<Identity> identities = new ArrayList<>();
        boolean waiting;
        String error;

        Facts(String locator, List<TraceSpan> spans) {
            this.locator = locator;
            this.spans = spans;
            for (var span : spans) {
                byId.put(span.spanId(), span);
            }
            for (var span : spans) {
                if (span.parentId() != null && byId.containsKey(span.parentId())) {
                    children.computeIfAbsent(span.parentId(), k -> new ArrayList<>()).add(span);
                }
                if (span.name().startsWith("eventconductor.task ") && span.has("eventconductor.step.executionId")) {
                    var execution = span.attr("eventconductor.step.executionId");
                    taskAttributes.computeIfAbsent(execution, k -> new HashMap<>()).putAll(span.attributes());
                    taskSpans.merge(execution, span, (a, b) -> a.durationMillis() >= b.durationMillis() ? a : b);
                }
            }
            walkIn = spans.stream().anyMatch(s -> s.isServer() && s.name().endsWith("/walk-ins"));
            for (var span : spans) {
                learn(span);
            }
            // A write step that wrote nothing: Opera already had this version, or a newer one — another
            // change of the same booking got there first.
            if (operaAction == null) {
                for (var span : spans) {
                    if ("upsert-reservation".equals(span.attr("eventconductor.step.id")) && span.has("eventconductor.step.type")
                            && "COMPLETED".equals(span.attr("eventconductor.step.status"))) {
                        var task = taskSpans.get(span.attr("eventconductor.step.executionId"));
                        var attributes = taskAttributes.getOrDefault(span.attr("eventconductor.step.executionId"), Map.of());
                        if (task != null && !attributes.containsKey("mapping.causes") && descendants(task).stream()
                                .filter(JourneyMapper::isOpera).map(JourneyMapper::operaWrite)
                                .noneMatch(w -> w != null && w.reservation())) {
                            operaAction = "stale";
                        }
                    }
                }
            }
        }

        /** Business facts any span may carry, whatever hop it becomes — or none. */
        void learn(TraceSpan span) {
            if (hotel == null && span.has("hotel.code")) {
                hotel = span.attr("hotel.code");
            }
            if (span.has("booking.event") && (event == null || !"relaunch".equals(event))) {
                event = span.attr("booking.event");
            }
            if (span.has("booking.version")) {
                version = parseLong(span.attr("booking.version"));
            }
            if (span.has("opera.reservation.id")) {
                operaReservationId = span.attr("opera.reservation.id");
                operaHotel = span.attr("opera.hotel");
                operaAction = span.attr("opera.action");
                if (version == null && span.has("opera.version")) {
                    version = parseLong(span.attr("opera.version"));
                }
            }
            if (span.has("mapping.translations")) {
                for (var t : span.attr("mapping.translations").split(";\\s*")) {
                    if (!t.isBlank() && !translations.contains(t.trim())) {
                        translations.add(t.trim());
                    }
                }
            }
            if (span.has("mapping.causes")) {
                for (var c : span.attr("mapping.causes").split(";\\s*")) {
                    var described = causeText(c);
                    if (!described.isBlank() && !causes.contains(described)) {
                        causes.add(described);
                    }
                }
            }
            if (span.has("mdm.identities") && identities.isEmpty()) {
                for (var i : span.attr("mdm.identities").split(",")) {
                    var parts = i.split(":");
                    if (parts.length == 3) {
                        identities.add(new Identity((int) parseLong(parts[0]).longValue(), parts[1], parts[2]));
                    }
                }
            }
            var url = url(span);
            if (url != null) {
                var written = WRITTEN_VERSION.matcher(url);
                if (written.find() && version == null) {
                    version = parseLong(written.group(1));
                }
            }
            if (isOpera(span)) {
                var write = operaWrite(span);
                if (write != null && write.reservation()) {
                    if (operaWrittenAt == 0 || span.endNanos() < operaWrittenAt) {
                        operaWrittenAt = span.endNanos();
                    }
                    if (operaAction == null) {
                        operaAction = write.action();
                    }
                }
                if (url != null) {
                    var reservation = OPERA_RESERVATION.matcher(url);
                    var reservations = OPERA_RESERVATIONS.matcher(url);
                    if (reservation.find()) {
                        operaHotel = operaHotel == null ? reservation.group(1) : operaHotel;
                        operaReservationId = operaReservationId == null ? reservation.group(2) : operaReservationId;
                    } else if (reservations.find() && operaHotel == null) {
                        operaHotel = reservations.group(1);
                    }
                }
            }
            var key = span.attr("eventconductor.process.businessKey");
            if (key != null) {
                var stay = STAY_KEY.matcher(key);
                if (stay.find()) {
                    operaHotel = operaHotel == null ? stay.group(1) : operaHotel;
                    operaReservationId = operaReservationId == null ? stay.group(2) : operaReservationId;
                }
            }
            if (isProcess(span)) {
                processes.put(span.attr("eventconductor.process.id"), new ProcessRef(span.attr("eventconductor.process.id"),
                        span.attr("eventconductor.workflow.id"), span.name(), span.attr("eventconductor.process.status"),
                        key, span.startNanos(), span.endNanos()));
            }
            if ("front-office".equals(span.service()) && span.name().contains("front-office-commands")) {
                if (frontOfficeAt == 0 || span.endNanos() < frontOfficeAt) {
                    frontOfficeAt = span.endNanos();
                }
            }
        }

        // ── hops ─────────────────────────────────────────────────────────────────────────────────

        java.util.Optional<Hop> hop(TraceSpan span) {
            var hop = switch (span.service()) {
                case "booking" -> crs(span);
                case "crs-integration" -> crsIntegration(span);
                case "orchestrator" -> engine(span);
                case "customer-mdm" -> mdm(span);
                case "front-office" -> frontOffice(span);
                case "mapping" -> mapping(span);
                case "integrations" -> integrations(span);
                default -> null;
            };
            // The innermost failure says why; the spans around it only that something under them failed.
            var cause = span.error() && children.getOrDefault(span.spanId(), List.of()).stream().noneMatch(TraceSpan::error);
            if (cause) {
                var lane = hop != null ? hop.lane() : laneOf(span.service());
                if (lane != null) {
                    var message = span.errorMessage() == null ? "sin detalle" : span.errorMessage();
                    error = error == null ? lane.label() + ": " + message : error;
                    hop = hop != null
                            ? hop.withTone(Tone.ERROR).withDetail(join(hop.detail(), "Error: " + message))
                            : hop(lane, span, "Error en " + lane.label(), message, Tone.ERROR, null);
                }
            }
            return java.util.Optional.ofNullable(hop);
        }

        Hop crs(TraceSpan span) {
            if (span.isServer() && span.name().startsWith("http ")) {
                var method = span.name().split(" ")[1];
                var route = span.has("uri") ? span.attr("uri") : span.name().substring(span.name().indexOf('/'));
                if (route.endsWith("/cancel")) {
                    return hop(Lane.CRS, span, "Reserva cancelada en el CRS", null, Tone.OK, null);
                }
                if (route.endsWith("/confirm")) {
                    return hop(Lane.CRS, span, "Reserva confirmada en el CRS", null, Tone.OK, null);
                }
                if ("post".equals(method) && "/bookings".equals(route)) {
                    return hop(Lane.CRS, span, walkIn ? "Reserva walk-in creada en el CRS" : "Reserva creada en el CRS",
                            "Localizador " + locator + (hotel == null ? "" : " · hotel " + hotel), Tone.OK, null);
                }
                if ("put".equals(method) && "/bookings/{id}".equals(route)) {
                    return hop(Lane.CRS, span, "Reserva modificada en el CRS", "Localizador " + locator, Tone.OK, null);
                }
                if (span.parentId() == null) {
                    return hop(Lane.CRS, span, "Cambio en el CRS", null, Tone.OK, null);
                }
                return null;
            }
            if ("consume-booking-commands process".equals(span.name())) {
                return hop(Lane.CRS, span, "La reserva del CRS apunta a Opera",
                        operaReservationId == null ? "Guarda la referencia de la reserva en Opera"
                                : "Guarda que en Opera es la reserva " + operaReservationId, Tone.OK, null);
            }
            return null;
        }

        Hop crsIntegration(TraceSpan span) {
            if ("consume-crs-events process".equals(span.name())) {
                return hop(Lane.CRS_INTEGRATION, span, "Evento del CRS recibido",
                        "Relee la reserva en el CRS y la publica como evento de negocio", Tone.OK, null);
            }
            if ("route-integration-events process".equals(span.name())) {
                var key = span.attr("eventconductor.business-key");
                var workflow = key == null ? null : key.substring(0, key.indexOf(':') > 0 ? key.indexOf(':') : key.length());
                var what = Kind.ofEvent(span.attr("booking.event"));
                return hop(Lane.CRS_INTEGRATION, span, "Evento → proceso",
                        join(what == null ? null : "Reserva " + what.label().toLowerCase(Locale.ROOT)
                                        + (span.has("booking.version") ? " (versión " + span.attr("booking.version") + ")" : ""),
                                workflow == null ? null : "arranca «" + workflowName(workflow) + "»"), Tone.OK, null);
            }
            if (span.isServer() && span.name().endsWith("/no-shows")) {
                return hop(Lane.CRS_INTEGRATION, span, "El hotel comunica el no-show",
                        "La recepción dice que no llegó: el CRS aplicará su regla", Tone.OK, null);
            }
            if (span.isServer() && span.name().endsWith("/walk-ins")) {
                return hop(Lane.CRS_INTEGRATION, span, "Walk-in: el front office pide la reserva",
                        "El CRS la crea como una reserva más, por el canal WALKIN", Tone.OK, null);
            }
            if (span.name().startsWith("projection-requests")) {
                return hop(Lane.CRS_INTEGRATION, span, "Reproyección pedida",
                        "Backfill: la reserva se vuelve a proyectar tal como está", Tone.OK, null);
            }
            return null;
        }

        Hop engine(TraceSpan span) {
            if (isProcess(span)) {
                var status = span.attr("eventconductor.process.status");
                var id = span.attr("eventconductor.process.id");
                return hop(Lane.ENGINE, span, "Proceso «" + span.name() + "»",
                        join(processStatus(status), id == null ? null : "id …" + id.substring(Math.max(0, id.length() - 8))),
                        "COMPLETED".equals(status) ? Tone.OK : Tone.INFO,
                        id == null ? null : "/workflow/processes/" + id);
            }
            var type = span.attr("eventconductor.step.type");
            if (type == null) {
                return null;
            }
            var attempts = parseLong(span.attr("eventconductor.step.attempts"));
            var retried = attempts != null && attempts > 0;
            return switch (type) {
                case "START", "END", "CHOICE" -> null;
                case "LOCK" -> {
                    var waited = span.durationMillis() >= 250;
                    yield hop(Lane.ENGINE, span, "Candado de la reserva tomado",
                            waited ? "Esperó " + Durations.words(Duration.ofMillis(span.durationMillis()))
                                    + " a que otro cambio de la misma reserva terminara de escribir en Opera"
                                    : "Nadie más la estaba escribiendo", waited ? Tone.WAIT : Tone.OK, null);
                }
                case "UNLOCK" -> hop(Lane.ENGINE, span, "Candado de la reserva soltado", null, Tone.OK, null);
                case "WAIT_FOR_MESSAGE" -> {
                    waiting = waiting || !"COMPLETED".equals(span.attr("eventconductor.step.status"));
                    yield hop(Lane.ENGINE, span, "Esperando a que se resuelvan sus causas",
                            causes.isEmpty() ? "No falla: espera, y sigue sola cuando se resuelvan" : "Espera a: " + String.join(" · ", causes),
                            Tone.WAIT, null);
                }
                case "ACTION" -> {
                    var lane = laneOfTopic(span.attr("eventconductor.step.topic"));
                    var task = taskAttributes.getOrDefault(span.attr("eventconductor.step.executionId"), Map.of());
                    var taskSpan = taskSpans.get(span.attr("eventconductor.step.executionId"));
                    var detail = describe(span.attr("eventconductor.step.id"), task, taskSpan);
                    var tone = retried ? Tone.WAIT : Tone.OK;
                    if (task.containsKey("mapping.causes") && !task.get("mapping.causes").isBlank()) {
                        tone = Tone.WAIT;
                    }
                    var status = span.attr("eventconductor.step.status");
                    if (status != null && !"COMPLETED".equals(status) && !"RUNNING".equals(status) && !"PENDING".equals(status)) {
                        tone = Tone.ERROR;
                        detail = join(detail, "estado " + status.toLowerCase(Locale.ROOT));
                    }
                    yield hop(lane, span, span.name(),
                            join(detail, retried ? attempts + (attempts == 1 ? " reintento" : " reintentos") : null), tone, null);
                }
                default -> hop(Lane.ENGINE, span, span.name(), null, Tone.INFO, null);
            };
        }

        Hop mdm(TraceSpan span) {
            if (span.isServer() && span.name().endsWith("/identities/resolve")) {
                return hop(Lane.MDM, span, "Identidad del cliente resuelta", identitiesText(), Tone.OK, null);
            }
            return null;
        }

        Hop frontOffice(TraceSpan span) {
            if (span.name().contains("front-office-commands")) {
                return hop(Lane.FRONT_OFFICE, span, "Estancia grabada en el front office",
                        "La recepción ya la ve, con los datos que Opera tiene", Tone.OK, null);
            }
            if (span.parentId() == null && walkIn) {
                return hop(Lane.FRONT_OFFICE, span, "Walk-in vendido en recepción", "Un huésped sin reserva en el mostrador",
                        Tone.OK, null);
            }
            if (span.parentId() == null) {
                var what = Kind.ofEvent(event);
                if (what == Kind.CHECK_IN) {
                    return hop(Lane.FRONT_OFFICE, span, "Check-in en recepción", "Sube al PMS, el maestro de la estancia", Tone.OK, null);
                }
                if (what == Kind.CHECK_OUT) {
                    return hop(Lane.FRONT_OFFICE, span, "Check-out en recepción", "Sube al PMS, el maestro de la estancia y del folio",
                            Tone.OK, null);
                }
                if (what == Kind.CHARGE) {
                    return hop(Lane.FRONT_OFFICE, span, "Cargo en recepción", "Va al folio del PMS, el maestro del folio", Tone.OK, null);
                }
                if (what == Kind.PAYMENT) {
                    return hop(Lane.FRONT_OFFICE, span, "Cobro en recepción", "Va al folio del PMS: su saldo es lo que queda por cobrar",
                            Tone.OK, null);
                }
                if (what == Kind.PAYMENT_REFUND) {
                    return hop(Lane.FRONT_OFFICE, span, "Cobro devuelto en recepción", "Se devuelve también en el folio del PMS",
                            Tone.OK, null);
                }
                if (what == Kind.CHARGE_VOID) {
                    return hop(Lane.FRONT_OFFICE, span, "Cargo anulado en recepción", "Se anula también en el folio del PMS",
                            Tone.OK, null);
                }
                if (what == Kind.NO_SHOW) {
                    return hop(Lane.FRONT_OFFICE, span, "No show en recepción", "Nadie de la reserva ha llegado: sube al PMS y de él al CRS",
                            Tone.OK, null);
                }
            }
            return null;
        }

        Hop mapping(TraceSpan span) {
            // An approval that resolved what this booking waited on starts the trace that resumes it.
            if (span.parentId() == null && span.isServer()) {
                return hop(Lane.MAPPING, span, "Se resuelve lo que faltaba", "Se aprueba la equivalencia o se resuelve la causa",
                        Tone.OK, null);
            }
            return null;
        }

        Hop integrations(TraceSpan span) {
            if (span.has("eventconductor.business-key") && java.util.Set.of("check-in", "check-out", "no-show", "charge", "charge-void")
                    .contains(span.attr("booking.event"))) {
                var key = span.attr("eventconductor.business-key");
                var workflow = key == null ? null : key.substring(0, key.indexOf(':') > 0 ? key.indexOf(':') : key.length());
                return hop(Lane.ENGINE, span, "La integración pms-fo lo recibe",
                        workflow == null ? "Lo que hizo recepción, al PMS" : "Arranca «" + workflowName(workflow) + "»", Tone.OK, null);
            }
            if (span.parentId() == null && span.isServer()) {
                return hop(Lane.MAPPING, span, "Se resuelve lo que faltaba", "Desde la integración del hotel", Tone.OK, null);
            }
            return null;
        }

        /** What a step did, from its worker's span: the attributes it set, and the calls it made. */
        String describe(String stepId, Map<String, String> task, TraceSpan taskSpan) {
            if (stepId == null) {
                return null;
            }
            var opera = taskSpan == null ? List.<TraceSpan>of() : descendants(taskSpan).stream().filter(JourneyMapper::isOpera).toList();
            var calls = opera.isEmpty() ? null : opera.size() + (opera.size() == 1 ? " llamada" : " llamadas") + " a Opera";
            if (stepId.startsWith("relaunch")) {
                return "Una instancia nueva del proceso relee la reserva y sigue";
            }
            return switch (stepId) {
                case "prepare" -> {
                    var translated = split(task.get("mapping.translations"));
                    var missing = split(task.get("mapping.causes"));
                    if (!missing.isEmpty()) {
                        yield "Espera: " + String.join(" · ", missing.stream().map(JourneyMapper::causeText).toList());
                    }
                    yield translated.isEmpty() ? "Traduce los códigos del CRS a los de Opera"
                            : translated.size() + " equivalencias: " + String.join(" · ", translated.stream().map(JourneyMapper::translation).toList());
                }
                case "ensure-guest-profile" -> {
                    var id = task.get("opera.profile.id");
                    var action = task.get("opera.profile.action");
                    String text = null;
                    if (id != null) {
                        text = "Perfil " + id + " " + switch (action == null ? "" : action) {
                            case "created" -> "creado en Opera";
                            case "updated" -> "actualizado en Opera";
                            default -> "ya estaba en Opera";
                        };
                    } else {
                        for (var call : opera) {
                            var write = operaWrite(call);
                            if (write != null && !write.reservation()) {
                                var profile = profileOf(taskSpan);
                                if (profile == null) {
                                    profile = profileId();
                                }
                                text = (write.action().equals("created") ? "Perfil creado en Opera" : "Perfil actualizado en Opera")
                                        + (profile == null ? "" : ": " + profile);
                            }
                        }
                    }
                    var customer = task.get("mdm.customer.id");
                    if (customer == null && taskSpan != null) {
                        customer = customerOf(taskSpan);
                    }
                    yield join(text == null ? "El huésped, como perfil de Opera" : text,
                            customer == null ? null : "cliente " + customer, calls);
                }
                case "upsert-reservation" -> join(operaReservationText(task), calls);
                case "cancel-reservation" -> join(operaReservationId == null ? "Cancelada en Opera"
                        : "Reserva " + operaReservationId + " cancelada en Opera", calls);
                case "project-stay" -> join(operaReservationId == null ? "Lee la reserva de Opera y la envía al front office"
                        : "Lee la reserva " + operaReservationId + " de Opera y la envía al front office", calls);
                case "annotate-pms-reference" -> operaReservationId == null ? "El CRS guarda la referencia de Opera"
                        : "El CRS guarda que en Opera es la " + operaReservationId;
                case "resolve-projection" -> "Libera lo que esperaba a que la reserva llegase a Opera";
                case "register-no-show" -> "El CRS cancela la reserva como no-show y aplica su penalización";
                case "assign-room" -> join(task.containsKey("opera.refused") ? "Opera no la asigna: " + task.get("opera.refused")
                        : task.containsKey("opera.room") ? "Habitación " + task.get("opera.room") + " asignada en Opera"
                        : "La habitación que dio recepción, en Opera (ya la tenía)", calls);
                case "check-in-reservation" -> join(task.containsKey("opera.refused") ? "Opera rechaza el check-in: " + task.get("opera.refused")
                        : "already-in-house".equals(task.get("opera.action")) ? "Opera ya la tenía en casa: no se escribe"
                        : "Check-in hecho en Opera" + (task.containsKey("opera.room") ? ", habitación " + task.get("opera.room") : ""), calls);
                case "check-out-reservation" -> join(task.containsKey("opera.refused") ? "Opera rechaza el check-out: " + task.get("opera.refused")
                        : "already-checked-out".equals(task.get("opera.action")) ? "Opera ya la tenía fuera: no se escribe"
                        : "Check-out hecho en Opera, con el cajero de la integración", calls);
                case "fetch-invoice" -> join(task.containsKey("opera.invoice") ? "Factura " + task.get("opera.invoice")
                        + " de Opera, al front office" : "Opera no emitió factura: el front office ofrece su proforma", calls);
                case "record-no-show" -> join(task.containsKey("opera.refused") ? "Opera no lo admite: " + task.get("opera.refused")
                        : "no-show-kept".equals(task.get("opera.action")) ? "Opera ya lo tenía: no se escribe"
                        : "No-show anotado en la reserva de Opera (su estado «No Show» lo pone la auditoría nocturna)", calls);
                case "report-no-show" -> "La integración crs-pms lo sube al CRS: arranca «Registrar no-show»";
                case "post-charge" -> join(task.containsKey("opera.refused") ? "Opera no admite el cargo: " + task.get("opera.refused")
                        : "charge-already-posted".equals(task.get("opera.action")) ? "Opera ya tenía el cargo en su folio: no se escribe"
                        : "Cargo en el folio de Opera" + (task.containsKey("opera.transaction.code")
                                ? " (código " + task.get("opera.transaction.code") + ")" : ""), calls);
                case "post-payment" -> join(task.containsKey("opera.refused") ? "Opera no admite el cobro: " + task.get("opera.refused")
                        : "payment-already-posted".equals(task.get("opera.action")) ? "Opera ya tenía el cobro en su folio: no se escribe"
                        : "Cobro en el folio de Opera" + (task.containsKey("opera.payment.method")
                                ? " (forma de pago " + task.get("opera.payment.method") + ")" : ""), calls);
                case "refund-payment" -> join(task.containsKey("opera.refused") ? "Opera no admite la devolución: " + task.get("opera.refused")
                        : "payment-already-refunded".equals(task.get("opera.action")) ? "Opera ya lo tenía devuelto: no se escribe"
                        : "Cobro devuelto en el folio de Opera (el mismo importe, en negativo)", calls);
                case "reverse-charge" -> join(task.containsKey("opera.refused") ? "Opera no admite la anulación: " + task.get("opera.refused")
                        : "charge-already-reversed".equals(task.get("opera.action")) ? "Opera ya lo tenía anulado: no se escribe"
                        : "Cargo anulado en el folio de Opera (el mismo importe, en negativo)", calls);
                default -> calls;
            };
        }

        String operaReservationText(Map<String, String> task) {
            var id = task.getOrDefault("opera.reservation.id", operaReservationId);
            var action = task.getOrDefault("opera.action", operaAction);
            var hotelText = operaHotel == null ? "" : " (hotel " + operaHotel + ")";
            var versionText = version == null ? "" : " · versión " + version + " del CRS";
            if (id == null) {
                return "La reserva, escrita en Opera";
            }
            return switch (action == null ? "" : action) {
                case "created" -> "Reserva " + id + " creada en Opera" + hotelText + versionText;
                case "updated" -> "Reserva " + id + " actualizada en Opera" + hotelText + versionText;
                case "stale" -> "Opera ya tenía la reserva " + id + " en esta versión o una más nueva: no se escribe";
                case "cancelled", "no-show" -> "Reserva " + id + " cancelada en Opera";
                case "checked-in" -> "Reserva " + id + " en casa en Opera" + hotelText;
                case "checked-out" -> "Reserva " + id + " con salida en Opera" + hotelText;
                default -> "Reserva " + id + " en Opera" + hotelText + versionText;
            };
        }

        String identitiesText() {
            if (identities.isEmpty()) {
                return "Reconoce a los pasajeros o da de alta clientes nuevos";
            }
            return String.join(" · ", identities.stream().map(i -> i.who() + " " + i.customerId() + ": " + i.how()).toList());
        }

        List<TraceSpan> descendants(TraceSpan span) {
            var list = new ArrayList<TraceSpan>();
            var queue = new ArrayDeque<>(children.getOrDefault(span.spanId(), List.of()));
            while (!queue.isEmpty()) {
                var next = queue.poll();
                list.add(next);
                queue.addAll(children.getOrDefault(next.spanId(), List.of()));
            }
            return list;
        }

        String profileOf(TraceSpan task) {
            for (var span : descendants(task)) {
                var url = url(span);
                if (url == null) {
                    continue;
                }
                var profile = OPERA_PROFILE.matcher(url);
                if (profile.find()) {
                    return profile.group(1);
                }
                var xref = OPERA_XREF.matcher(url);
                if (xref.find()) {
                    return xref.group(1);
                }
            }
            return null;
        }

        /** The guest's Opera profile, wherever in the trace it was named — a later read, a cross reference. */
        String profileId() {
            for (var span : spans) {
                if (span.has("opera.profile.id")) {
                    return span.attr("opera.profile.id");
                }
                var url = url(span);
                if (url == null) {
                    continue;
                }
                var profile = OPERA_PROFILE.matcher(url);
                if (profile.find()) {
                    return profile.group(1);
                }
                var xref = OPERA_XREF.matcher(url);
                if (xref.find()) {
                    return xref.group(1);
                }
            }
            return null;
        }

        String customerOf(TraceSpan task) {
            for (var span : descendants(task)) {
                var url = url(span);
                if (url != null) {
                    var customer = MDM_CUSTOMER.matcher(url);
                    if (customer.find()) {
                        return customer.group(1);
                    }
                }
            }
            return null;
        }

        // ── the whole ────────────────────────────────────────────────────────────────────────────

        Journey journey(String traceId, List<Hop> hops, long nowNanos) {
            var start = hops.stream().filter(h -> h.lane() == Lane.CRS || h.lane() == Lane.FRONT_OFFICE
                            || h.lane() == Lane.CRS_INTEGRATION || h.lane() == Lane.MAPPING)
                    .mapToLong(Hop::startNanos).min()
                    .orElse(spans.stream().mapToLong(TraceSpan::startNanos).min().orElse(0));
            var end = spans.stream().mapToLong(TraceSpan::endNanos).max().orElse(start);
            var kind = kind();
            var toOpera = operaWrittenAt > 0 ? Duration.ofNanos(operaWrittenAt - start) : stepEnd(start, "upsert-reservation", "cancel-reservation",
                    "check-in-reservation", "check-out-reservation", "record-no-show",
                    "post-charge", "reverse-charge", "post-payment", "refund-payment");
            var toFrontOffice = frontOfficeAt > 0 ? Duration.ofNanos(frontOfficeAt - start) : null;
            Outcome outcome;
            String detail;
            // An error the engine retried past is not how it ended: a write that failed while Opera was
            // unreachable and went in on a later attempt is DONE, and says it took retries.
            var retried = error != null && toOpera != null;
            if (error != null && !retried) {
                outcome = Outcome.FAILED;
                detail = humanError(error);
            } else if (waiting || (!causes.isEmpty() && toOpera == null)) {
                outcome = Outcome.WAITING;
                detail = causes.isEmpty() ? "Espera a que se resuelvan sus causas" : "Espera a: " + String.join(" · ", causes);
            } else if (processes.isEmpty()) {
                var young = nowNanos - end < SETTLING.toNanos();
                outcome = young ? Outcome.IN_PROGRESS : Outcome.NOT_PROJECTED;
                detail = young ? "Acaba de empezar: la traza sigue llegando"
                        : "No arrancó ningún proceso: el hotel no tenía una integración activa";
            } else if (toOpera != null && processes.values().stream().allMatch(p -> "COMPLETED".equals(p.status()))) {
                outcome = Outcome.DONE;
                detail = (toFrontOffice != null ? "En Opera y en el front office" : "En Opera")
                        + (retried ? ", tras reintentos (" + humanError(error) + ")" : "");
            } else if (toOpera != null) {
                outcome = Outcome.DONE;
                detail = "En Opera" + (retried ? ", tras reintentos (" + humanError(error) + ")" : "");
            } else {
                outcome = Outcome.IN_PROGRESS;
                detail = nowNanos - end < SETTLING.toNanos() ? "En curso: la traza sigue llegando" : "Sin llegar a Opera en esta traza";
            }
            return new Journey(traceId, locator, hotel, kind, version, start, end, toOpera, toFrontOffice, outcome, detail,
                    operaHotel, operaReservationId, operaAction, List.copyOf(processes.values()), List.copyOf(hops),
                    List.copyOf(translations.stream().map(JourneyMapper::translation).toList()), List.copyOf(causes),
                    List.copyOf(identities));
        }

        Kind kind() {
            var tagged = Kind.ofEvent(event);
            if (tagged == Kind.CREATED && walkIn) {
                return Kind.WALK_IN;
            }
            if (tagged != null) {
                return tagged;
            }
            if (spans.stream().anyMatch(s -> s.isServer() && s.name().endsWith("/no-shows"))
                    || processes.values().stream().anyMatch(p -> "registrar-no-show-pms".equals(p.workflowId()))) {
                return Kind.NO_SHOW;
            }
            if (processes.values().stream().anyMatch(p -> "registrar-checkin".equals(p.workflowId()))) {
                return Kind.CHECK_IN;
            }
            if (processes.values().stream().anyMatch(p -> "registrar-checkout".equals(p.workflowId()))) {
                return Kind.CHECK_OUT;
            }
            if (processes.values().stream().anyMatch(p -> "registrar-cargo".equals(p.workflowId()))) {
                return Kind.CHARGE;
            }
            if (processes.values().stream().anyMatch(p -> "anular-cargo".equals(p.workflowId()))) {
                return Kind.CHARGE_VOID;
            }
            if (processes.values().stream().anyMatch(p -> "registrar-cobro".equals(p.workflowId()))) {
                return Kind.PAYMENT;
            }
            if (processes.values().stream().anyMatch(p -> "devolver-cobro".equals(p.workflowId()))) {
                return Kind.PAYMENT_REFUND;
            }
            if (walkIn) {
                return Kind.WALK_IN;
            }
            if (spans.stream().anyMatch(s -> "booking".equals(s.service()) && s.isServer() && s.name().endsWith("/cancel"))
                    || processes.values().stream().anyMatch(p -> "proyectar-cancelacion".equals(p.workflowId()))) {
                return Kind.CANCELLED;
            }
            if (spans.stream().anyMatch(s -> "booking".equals(s.service()) && "http put /bookings/{id}".equals(s.name()))) {
                return Kind.MODIFIED;
            }
            if (spans.stream().anyMatch(s -> "booking".equals(s.service()) && "http post /bookings".equals(s.name()))) {
                return Kind.CREATED;
            }
            if (spans.stream().anyMatch(s -> s.name().startsWith("projection-requests"))) {
                return Kind.BACKFILL;
            }
            if (processes.values().stream().anyMatch(p -> p.businessKey() != null && p.businessKey().contains(">r"))) {
                return Kind.RELAUNCH;
            }
            return Kind.OTHER;
        }

        Duration stepEnd(long start, String... stepIds) {
            for (var span : spans) {
                for (var id : stepIds) {
                    var task = taskAttributes.getOrDefault(span.attr("eventconductor.step.executionId"), Map.of());
                    if (id.equals(span.attr("eventconductor.step.id")) && "COMPLETED".equals(span.attr("eventconductor.step.status"))
                            && !task.containsKey("mapping.causes")) {
                        return Duration.ofNanos(span.endNanos() - start);
                    }
                }
            }
            return null;
        }

        Hop hop(Lane lane, TraceSpan span, String title, String detail, Tone tone, String link) {
            return new Hop(lane, span.startNanos(), span.endNanos(), title, detail, tone, link);
        }
    }

    // ── small vocabulary ─────────────────────────────────────────────────────────────────────────

    /** A write to Opera: what kind, and whether it was the reservation (or a profile, a deposit). */
    record OperaWrite(String action, boolean reservation) {
    }

    static OperaWrite operaWrite(TraceSpan span) {
        var method = span.has("method") ? span.attr("method").toUpperCase(Locale.ROOT)
                : span.name().startsWith("http ") ? span.name().split(" ")[1].toUpperCase(Locale.ROOT) : "";
        var uri = span.has("uri") ? span.attr("uri") : "";
        if (!"POST".equals(method) && !"PUT".equals(method) && !"DELETE".equals(method)) {
            return null;
        }
        if (uri.contains("cancellations") || (url(span) != null && url(span).contains("cancellations"))) {
            return new OperaWrite("cancelled", true);
        }
        var full = url(span) == null ? uri : uri + " " + url(span);
        if (full.contains("/checkIns")) {
            return new OperaWrite("checked-in", true);
        }
        if (full.contains("/checkOuts")) {
            return new OperaWrite("checked-out", true);
        }
        if (full.contains("/roomAssignments")) {
            return new OperaWrite("room-assigned", true);
        }
        if (full.contains("/csh/v1/") && full.contains("/folios")) {
            return new OperaWrite("invoice", false);
        }
        if (uri.startsWith("/rsv/v1/hotels/{h}/reservations")) {
            if (uri.contains("deposit") || uri.contains("payment")) {
                return new OperaWrite("deposit", false);
            }
            return new OperaWrite("POST".equals(method) && uri.equals("/rsv/v1/hotels/{h}/reservations") ? "created" : "updated", true);
        }
        if (uri.startsWith("/crm/v1/profiles")) {
            return new OperaWrite("POST".equals(method) ? "created" : "updated", false);
        }
        return null;
    }

    static boolean isOpera(TraceSpan span) {
        if (!span.isClient()) {
            return false;
        }
        var client = span.attr("client.name");
        var url = url(span);
        return (client != null && client.contains("hospitality-api"))
                || (url != null && (url.contains("/rsv/v1/") || url.contains("/crm/v1/") || url.contains("/fof/v1/")
                        || url.contains("/csh/v1/")));
    }

    static boolean isProcess(TraceSpan span) {
        return "orchestrator".equals(span.service()) && span.has("eventconductor.workflow.id")
                && span.has("eventconductor.process.status") && !span.has("eventconductor.step.type")
                && !span.name().startsWith("eventconductor.");
    }

    static String url(TraceSpan span) {
        var url = span.attr("http.url");
        if (url == null) {
            url = span.attr("url.full");
        }
        return url == null ? null : URLDecoder.decode(url, StandardCharsets.UTF_8);
    }

    static Lane laneOfTopic(String topic) {
        if (topic == null) {
            return Lane.ENGINE;
        }
        return switch (topic) {
            case "mapping" -> Lane.MAPPING;
            case "pms-integration" -> Lane.OPERA;
            case "crs-integration" -> Lane.CRS_INTEGRATION;
            case "booking", "crs" -> Lane.CRS;
            case "front-office" -> Lane.FRONT_OFFICE;
            case "customer-mdm", "mdm" -> Lane.MDM;
            default -> Lane.ENGINE;
        };
    }

    static Lane laneOf(String service) {
        return switch (service) {
            case "booking" -> Lane.CRS;
            case "crs-integration" -> Lane.CRS_INTEGRATION;
            case "orchestrator", "integrations" -> Lane.ENGINE;
            case "mapping" -> Lane.MAPPING;
            case "customer-mdm" -> Lane.MDM;
            case "pms-integration" -> Lane.OPERA;
            case "front-office" -> Lane.FRONT_OFFICE;
            default -> null;
        };
    }

    static String workflowName(String workflowId) {
        return switch (workflowId) {
            case "proyectar-reserva" -> "Proyectar reserva";
            case "proyectar-cancelacion" -> "Proyectar cancelación";
            case "proyectar-estancia" -> "Proyectar estancia";
            case "registrar-no-show" -> "Registrar no-show";
            case "registrar-checkin" -> "Registrar check-in";
            case "registrar-checkout" -> "Registrar check-out";
            case "registrar-no-show-pms" -> "Registrar no-show en el PMS";
            case "registrar-cargo" -> "Registrar cargo";
            case "anular-cargo" -> "Anular cargo";
            case "registrar-cobro" -> "Registrar cobro";
            case "devolver-cobro" -> "Devolver cobro";
            default -> workflowId;
        };
    }

    static String processStatus(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "COMPLETED" -> "terminado";
            case "RUNNING", "PENDING" -> "en marcha";
            case "WAITING", "PAUSED" -> "esperando";
            case "FAILED", "ERROR" -> "con error";
            case "CANCELLED" -> "cancelado";
            default -> status.toLowerCase(Locale.ROOT);
        };
    }

    /** "KEY=description", as a cause is put on a span, in words. */
    static String causeText(String tagged) {
        var equals = tagged.indexOf('=');
        return equals < 0 ? CauseText.of(tagged.trim(), null)
                : CauseText.of(tagged.substring(0, equals).trim(), tagged.substring(equals + 1).trim());
    }

    /** "ROOM_TYPE DBL=DBLX" as "Tipo de habitación DBL → DBLX". */
    public static String translation(String raw) {
        var space = raw.indexOf(' ');
        var equals = raw.indexOf('=');
        if (space < 0 || equals < space) {
            return raw;
        }
        return codeType(raw.substring(0, space)) + " " + raw.substring(space + 1, equals) + " → " + raw.substring(equals + 1);
    }

    static String codeType(String type) {
        return switch (type) {
            case "HOTEL" -> "Hotel";
            case "ROOM_TYPE" -> "Tipo de habitación";
            case "RATE_PLAN" -> "Tarifa";
            case "BOARD" -> "Régimen";
            case "CHANNEL" -> "Canal";
            case "PAYMENT_METHOD" -> "Forma de pago";
            case "CANCELLATION_REASON" -> "Motivo de cancelación";
            case "PARTNER_TYPE" -> "Tipo de interlocutor";
            case "MARKET" -> "Mercado";
            default -> type;
        };
    }

    static List<String> split(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(joined.split(";\\s*")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    static String join(String... parts) {
        var kept = java.util.Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).toList();
        return kept.isEmpty() ? null : String.join(" · ", kept);
    }

    static Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * An error as «Cómo acabó» says it: what happened, in the desk's words — not the technical message
     * with OHIP's URL (that stays in «Paso a paso» and in the trace).
     */
    public static String humanError(String error) {
        if (error == null || error.isBlank()) {
            return "";
        }
        var e = error.toLowerCase(java.util.Locale.ROOT);
        if (e.contains("simulación: opera no responde")) {
            return "Opera no respondía (simulación)";
        }
        if (e.contains("i/o error") || e.contains("timed out") || e.contains("timeout") || e.contains("connection refused")
                || e.contains("connect")) {
            return "Opera no respondía";
        }
        if (e.matches("(?s).*ohip (5\\d\\d|429).*")) {
            return "Opera respondió con un error temporal";
        }
        if (e.contains("ohip 401")) {
            return "Opera no aceptó las credenciales";
        }
        var plain = error.replaceAll("\"?https?://[^\\s\"]+\"?", "").replaceAll("\\([A-Z]+ /[^)]*\\)", "")
                .replaceAll("\\s+", " ").strip();
        return plain.length() > 160 ? plain.substring(0, 157) + "…" : plain;
    }
}
