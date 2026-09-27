package io.mateu.ecdemo1.frontoffice.ui.checkin;

import io.mateu.core.infra.declarative.orchestrators.editableview.EditableView;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.application.KardexService;
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

  /** A prototype bean: Mateu takes the island from Spring; the Identidad step asks Mateu for one too. */
  public DocumentoView(StayQueries queries, KardexService kardex) {
    this.queries = queries;
    this.kardex = kardex;
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
  }

  /** Editor: contact data editable; identity fields stay read-only. */
  @Getter
  @Setter
  @Title("Documento")
  @SubscribeTo(event = "pax-seleccionado", action = "cambiarPax")
  public static class DocumentoEditor {
    @Label("Documento")
    String documento;

    @Label("Nombre")
    String nombre;

    @Label("Email")
    String email;

    @Label("Teléfono")
    String telefono;
  }

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
    return datos;
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
    return editor;
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
              .withCommand(UICommand.dispatchEvent("documento-escaneado"))
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
        var result =
            new ArrayList<Object>((java.util.Collection<?>) super.handleAction(actionId, httpRequest));
        result.add(UICommand.dispatchEvent("documento-escaneado"));
        yield result;
      }
      case "reloadDocumento" -> reload();
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
