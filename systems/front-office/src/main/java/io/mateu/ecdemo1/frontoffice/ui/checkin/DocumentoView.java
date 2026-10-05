package io.mateu.ecdemo1.frontoffice.ui.checkin;

import io.mateu.core.infra.declarative.orchestrators.editableview.EditableView;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.application.KardexService;
import io.mateu.ecdemo1.frontoffice.application.RegistrationRequirementsService;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRequirements;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Moment;
import io.mateu.ecdemo1.frontoffice.application.StayQueries;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.PlainText;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.SubscribeTo;
import io.mateu.uidl.annotations.SubscribesTo;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.LongTask;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.Action;
import io.mateu.uidl.interfaces.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * The Documento block of the Identidad step as an ORCHESTRATED embedded element (a {@link
 * EditableView} island, {@code @Inline} inside the step's section): the backend decides which of
 * its three states renders —
 *
 * <ul>
 *   <li><b>Sin datos</b> — a warning notice plus a "Escanear documento" button; the scan streams a
 *       simulated progress dialog ({@link LongTask} over SSE) and, when done, verifies the identity
 *       in the domain and reloads the island into…
 *   <li><b>Hay datos</b> — a property list with the document data and the built-in Edit toolbar
 *       button;
 *   <li><b>Editor</b> — the editable form; Save persists and lands back on "hay datos" (the
 *       standard EditableView save → /view cycle).
 * </ul>
 *
 * <p>The island works on ONE pax of the reservation at a time: pax 1 is the stay's main guest
 * (Guest aggregate), pax 2..N are the stay's companions. The host seeds {@code stayId} and {@code
 * paxIndex} through the embedded field's initialData, and the registroPax band switches the pax by
 * dispatching {@code pax-seleccionado} — the subscription on the loaded models runs {@code
 * cambiarPax}, which route-flips so the embedded mediator re-renders with the new pax.
 */
@Getter
@Setter
@UI("/checkin-documento")
@Title("Documento")
@Service
@Scope("prototype")
public class DocumentoView extends EditableView<Object, DocumentoView.DocumentoEditor> {

  @Getter(AccessLevel.NONE) final StayQueries queries;
  @Getter(AccessLevel.NONE) final KardexService kardex;
  @Getter(AccessLevel.NONE) final RegistrationRequirementsService registration;

  /** A prototype bean: Mateu takes the island from Spring; the Identidad step asks Mateu for one too. */
  public DocumentoView(StayQueries queries, KardexService kardex, RegistrationRequirementsService registration) {
    this.queries = queries;
    this.kardex = kardex;
    this.registration = registration;
  }

  @Hidden String stayId;
  @Hidden int paxIndex = 1;
  @Hidden boolean vistaAlterna;

  // ── the three states' models ─────────────────────────────────────────────────

  /** Sin datos: aviso + escanear (the subscriptions reload the island after scan / pax switch). */
  @Getter
  @Setter
  @Title("Documento")
  @SubscribesTo({
    @SubscribeTo(event = "documento-escaneado", action = "reloadDocumento"),
    @SubscribeTo(event = "pax-seleccionado", action = "cambiarPax")
  })
  public static class DocumentoPendiente {
    @io.mateu.uidl.annotations.Notice(theme = "warning")
    String aviso = "Documento pendiente de escaneo";

    @Label("")
    Button escanear = new Button("Escanear documento", "escanear");

    @Label("")
    Button rellenar = new Button("Rellenar a mano", "edit");
  }

  /** Hay datos: read-only property list (label left / value right). */
  @Getter
  @Setter
  @Title("Documento")
  @SubscribeTo(event = "pax-seleccionado", action = "cambiarPax")
  public static class DocumentoDatos {
    @Section(value = "", propertyList = true, frameless = true)
    @Label("Documento")
    String documento;

    @Label("Nombre")
    String nombre;

    @Label("Email")
    String email;

    @Label("Teléfono")
    String telefono;

    /** What the destination's registration rules ask of this pax, and what of it the kárdex has. */
    @Label("Registro de viajeros")
    String registro;
  }

  /**
   * Editor: identity, contact and the registration data the destination's rules ask for — the fields
   * they require are marked required for this pax's nationality and age ({@link #isRequired}), and the
   * legal basis is said above them.
   */
  @Getter
  @Setter
  @Title("Documento")
  @SubscribeTo(event = "pax-seleccionado", action = "cambiarPax")
  public static class DocumentoEditor implements io.mateu.uidl.interfaces.RequiredSupplier {
    @io.mateu.uidl.annotations.Notice(theme = "info")
    String baseLegal;

    @Label("Documento")
    String documento;

    @Label("Nombre")
    String nombre;

    @Label("Email")
    String email;

    @Label("Teléfono")
    String telefono;

    @Section("Registro de viajeros")
    @Label("Tipo de documento")
    String tipoDocumento;

    @Label("País de expedición")
    String paisExpedicion;

    @Label("Caducidad del documento")
    java.time.LocalDate caducidad;

    @Label("Nacionalidad")
    String nacionalidad;

    @Label("Fecha de nacimiento")
    java.time.LocalDate fechaNacimiento;

    @Label("Lugar de nacimiento")
    String lugarNacimiento;

    @Label("Sexo")
    String sexo;

    @Label("Dirección")
    String direccion;

    @Label("Ciudad")
    String ciudad;

    @Label("Código postal")
    String codigoPostal;

    @Label("País de residencia")
    String paisResidencia;

    @Label("Adulto responsable y parentesco")
    String tutor;

    /** The rules' required fields for this pax, comma separated (RegistrationRuleChanged.Field names). */
    @Hidden String requeridos;

    @Override
    public boolean isRequired(String fieldName, HttpRequest httpRequest) {
      var field = FIELDS.get(fieldName);
      return field != null && requeridos != null && java.util.Arrays.asList(requeridos.split(",")).contains(field.name());
    }
  }

  /** Each editor field, and the registration field it is. */
  static final java.util.Map<String, Field> FIELDS = java.util.Map.ofEntries(
      java.util.Map.entry("documento", Field.DOCUMENT_NUMBER), java.util.Map.entry("tipoDocumento", Field.DOCUMENT_TYPE),
      java.util.Map.entry("paisExpedicion", Field.DOCUMENT_ISSUING_COUNTRY), java.util.Map.entry("caducidad", Field.DOCUMENT_EXPIRY),
      java.util.Map.entry("nacionalidad", Field.NATIONALITY), java.util.Map.entry("fechaNacimiento", Field.BIRTH_DATE),
      java.util.Map.entry("lugarNacimiento", Field.BIRTH_PLACE), java.util.Map.entry("sexo", Field.SEX),
      java.util.Map.entry("direccion", Field.ADDRESS), java.util.Map.entry("ciudad", Field.CITY),
      java.util.Map.entry("codigoPostal", Field.POSTAL_CODE), java.util.Map.entry("paisResidencia", Field.COUNTRY_OF_RESIDENCE),
      java.util.Map.entry("tutor", Field.GUARDIAN));

  // ── state selection ──────────────────────────────────────────────────────────

  @Override
  public Object view(HttpRequest httpRequest) {
    var pax = pax();
    if (!pax.complete()) {
      var pendiente = new DocumentoPendiente();
      pendiente.setAviso(
          paxIndex() == 1
              ? "Documento pendiente de escaneo"
              : "Documento del huésped " + paxIndex() + " pendiente de escaneo");
      return pendiente;
    }
    var datos = new DocumentoDatos();
    datos.setDocumento("✓ Verificado — " + pax.document());
    datos.setNombre(pax.name());
    datos.setEmail(pax.email());
    datos.setTelefono(pax.phone());
    datos.setRegistro(registro());
    return datos;
  }

  /** «Exige: nacionalidad ✓, fecha de nacimiento ✓, dirección — falta · RD 933/2021», or null with no rule. */
  String registro() {
    if (stayId == null || stayId.isBlank()) {
      return null;
    }
    var stay = queries.view(stayId).stay();
    var result = registration.required(stay, paxIndex(), Moment.CHECK_IN);
    if (result.fields().isEmpty()) {
      return "Este destino no exige más datos a este huésped";
    }
    var values = registration.values(stay, paxIndex());
    var parts = result.fields().stream().filter(f -> f != Field.SIGNATURE)
        .map(f -> RegistrationRequirements.label(f) + (blank(values.get(f)) ? " — falta" : " ✓")).toList();
    return "Exige: " + String.join(", ", parts)
        + (result.legalBases().isEmpty() ? "" : " · " + String.join("; ", result.legalBases()));
  }

  @Override
  public DocumentoEditor editor(HttpRequest httpRequest) {
    var pax = pax();
    var editor = new DocumentoEditor();
    if (pax.complete()) {
      editor.setDocumento(pax.document());
      editor.setNombre(pax.name());
      editor.setEmail(pax.email());
      editor.setTelefono(pax.phone());
    } else {
      // rellenado manual: solo el nombre provisional del hueco como punto de partida
      editor.setNombre(pax.name());
    }
    if (stayId != null && !stayId.isBlank()) {
      var stay = queries.view(stayId).stay();
      var values = registration.values(stay, paxIndex());
      editor.setTipoDocumento(values.get(Field.DOCUMENT_TYPE));
      editor.setPaisExpedicion(values.get(Field.DOCUMENT_ISSUING_COUNTRY));
      editor.setCaducidad(date(values.get(Field.DOCUMENT_EXPIRY)));
      editor.setNacionalidad(values.get(Field.NATIONALITY));
      editor.setFechaNacimiento(date(values.get(Field.BIRTH_DATE)));
      editor.setLugarNacimiento(values.get(Field.BIRTH_PLACE));
      editor.setSexo(values.get(Field.SEX));
      editor.setDireccion(values.get(Field.ADDRESS));
      editor.setCiudad(values.get(Field.CITY));
      editor.setCodigoPostal(values.get(Field.POSTAL_CODE));
      editor.setPaisResidencia(values.get(Field.COUNTRY_OF_RESIDENCE));
      editor.setTutor(values.get(Field.GUARDIAN));
      var result = registration.required(stay, paxIndex(), Moment.CHECK_IN);
      editor.setRequeridos(String.join(",", result.fields().stream().map(Enum::name).toList()));
      editor.setBaseLegal(result.fields().isEmpty() ? null
          : "Datos obligatorios para el registro de este huésped"
              + (result.legalBases().isEmpty() ? "" : " — " + String.join("; ", result.legalBases())));
    }
    return editor;
  }

  /** The registration data an edit carries, by field. */
  static java.util.Map<Field, String> registrationData(DocumentoEditor e) {
    var values = new java.util.EnumMap<Field, String>(Field.class);
    values.put(Field.DOCUMENT_TYPE, e.getTipoDocumento());
    values.put(Field.DOCUMENT_ISSUING_COUNTRY, upper(e.getPaisExpedicion()));
    values.put(Field.DOCUMENT_EXPIRY, e.getCaducidad() == null ? null : e.getCaducidad().toString());
    values.put(Field.NATIONALITY, upper(e.getNacionalidad()));
    values.put(Field.BIRTH_DATE, e.getFechaNacimiento() == null ? null : e.getFechaNacimiento().toString());
    values.put(Field.BIRTH_PLACE, e.getLugarNacimiento());
    values.put(Field.SEX, e.getSexo());
    values.put(Field.ADDRESS, e.getDireccion());
    values.put(Field.CITY, e.getCiudad());
    values.put(Field.POSTAL_CODE, e.getCodigoPostal());
    values.put(Field.COUNTRY_OF_RESIDENCE, upper(e.getPaisResidencia()));
    values.put(Field.GUARDIAN, e.getTutor());
    return values;
  }

  static String upper(String s) {
    return s == null ? null : s.trim().toUpperCase(java.util.Locale.ROOT);
  }

  static java.time.LocalDate date(String s) {
    try {
      return s == null || s.isBlank() ? null : java.time.LocalDate.parse(s.trim());
    } catch (RuntimeException e) {
      return null;
    }
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  @Override
  public void save(HttpRequest httpRequest) {
    var edited = httpRequest.getInitiatorState(DocumentoEditor.class);
    if (edited == null) {
      return;
    }
    if (stayId == null || stayId.isBlank()) {
      return;
    }
    if (pax().complete()) {
      kardex.contactUpdated(stayId, paxIndex(), edited.getEmail(), edited.getTelefono());
    } else {
      kardex.registered(stayId, paxIndex(), edited.getDocumento(), edited.getNombre(), edited.getEmail(),
          edited.getTelefono());
    }
    kardex.registrationData(stayId, paxIndex(), registrationData(edited));
  }

  /** No Edit button while there is no data — the empty state only offers the scan. */
  @Override
  public boolean readOnly() {
    return !pax().complete();
  }

  // ── actions ──────────────────────────────────────────────────────────────────

  @Override
  public List<Action> actions(HttpRequest httpRequest) {
    var actions = new ArrayList<>(super.actions(httpRequest));
    actions.add(Action.builder().id("escanear").sse(true).build());
    actions.add(Action.builder().id("reloadDocumento").build());
    actions.add(Action.builder().id("cambiarPax").build());
    return actions;
  }

  @Override
  public Object handleAction(String actionId, HttpRequest httpRequest) {
    return switch (actionId) {
      case "escanear" ->
          LongTask.create("Escaneando documento…")
              .withProgressBar()
              .done("Documento verificado", "Identidad leída del documento")
              .closeAfter(1)
              // avanzar: the wizard and this island go on to the next pax still lacking identity
              .withCommand(UICommand.dispatchEvent("documento-escaneado", java.util.Map.of("avanzar", true)))
              .run(
                  progress ->
                      Flux.range(1, 4)
                          .delayElements(Duration.ofMillis(450))
                          .map(
                              i -> {
                                if (i == 4 && stayId != null && !stayId.isBlank()) {
                                  kardex.scanned(stayId, paxIndex());
                                }
                                return progress.step(SCAN_STEPS[i - 1], i / 4.0);
                              }));
      case "save" -> {
        // registering a pending pax by hand completes it like a scan: go on to the next one; a
        // contact edit of a complete pax stays on it
        boolean registro = !pax().complete();
        var result =
            new ArrayList<Object>((java.util.Collection<?>) super.handleAction(actionId, httpRequest));
        result.add(UICommand.dispatchEvent("documento-escaneado", java.util.Map.of("avanzar", registro)));
        yield result;
      }
      case "reloadDocumento" -> {
        // the same next pax the wizard selects (both read it from the stay), so the band and the
        // island agree whichever reload lands last
        var avanzar = httpRequest.runActionRq().parameters() == null ? null
            : httpRequest.runActionRq().parameters().get("avanzar");
        if (Boolean.parseBoolean(String.valueOf(avanzar)) && stayId != null && !stayId.isBlank()) {
          var next = queries.nextPendingPax(stayId, paxIndex());
          if (next > 0) {
            paxIndex = next;
          }
        }
        yield reload();
      }
      case "cambiarPax" -> {
        var raw = httpRequest.runActionRq().parameters().get("paxIndex");
        if (raw instanceof Number number) {
          paxIndex = number.intValue();
        } else if (raw != null) {
          paxIndex = (int) Double.parseDouble(String.valueOf(raw));
        }
        yield reload();
      }
      default -> super.handleAction(actionId, httpRequest);
    };
  }

  /** The embedded mediator only re-renders on a route change — alternate the view route. */
  private Object reload() {
    vistaAlterna = !vistaAlterna;
    setRouteTo(vistaAlterna ? "/view" : "/");
    return new State(this);
  }

  private static final String[] SCAN_STEPS = {
    "Encendiendo el escáner…", "Leyendo el documento…", "Extrayendo los datos…", "Verificando la identidad…"
  };

  // ── the pax the island is working on (main guest or companion) ───────────────

  private int paxIndex() {
    return paxIndex < 1 ? 1 : paxIndex;
  }

  private Pax pax() {
    return paxIndex() == 1 ? new MainGuestPax() : new CompanionPax(paxIndex());
  }

  /** Uniform identity/contact access over the main guest (pax 1) and the companions (pax 2..N). */
  private interface Pax {
    boolean complete();

    String document();

    String name();

    String email();

    String phone();
  }

  private class MainGuestPax implements Pax {
    private io.mateu.ecdemo1.frontoffice.domain.guest.Guest guest() {
      if (stayId == null || stayId.isBlank()) {
        return null;
      }
      return queries.view(stayId).guest();
    }

    @Override
    public boolean complete() {
      var guest = guest();
      return guest != null && guest.identityComplete();
    }

    @Override
    public String document() {
      var guest = guest();
      return guest == null ? null : guest.document();
    }

    @Override
    public String name() {
      var guest = guest();
      return guest == null ? null : guest.name();
    }

    @Override
    public String email() {
      var guest = guest();
      return guest == null ? null : guest.email();
    }

    @Override
    public String phone() {
      var guest = guest();
      return guest == null ? null : guest.phone();
    }
  }

  private class CompanionPax implements Pax {
    private final int number;

    private CompanionPax(int number) {
      this.number = number;
    }

    private Companion companion() {
      if (stayId == null || stayId.isBlank()) {
        return null;
      }
      return queries.view(stayId).stay().companionAt(number);
    }

    @Override
    public boolean complete() {
      var companion = companion();
      return companion != null && companion.identityComplete();
    }

    @Override
    public String document() {
      var companion = companion();
      return companion == null ? null : companion.document();
    }

    @Override
    public String name() {
      var companion = companion();
      return companion == null ? "Huésped " + number : companion.name();
    }

    @Override
    public String email() {
      var companion = companion();
      return companion == null ? null : companion.email();
    }

    @Override
    public String phone() {
      var companion = companion();
      return companion == null ? null : companion.phone();
    }
  }
}
