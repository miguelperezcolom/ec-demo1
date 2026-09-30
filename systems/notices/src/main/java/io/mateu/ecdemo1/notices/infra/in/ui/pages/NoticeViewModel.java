package io.mateu.ecdemo1.notices.infra.in.ui.pages;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeType;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.SubjectType;
import io.mateu.ecdemo1.notices.application.Notices;
import io.mateu.ecdemo1.notices.store.Notice;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Multiline;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.OptionsSupplier;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

/**
 * A notice's form. A reservation's or a partner's is created and changed here; a customer's is
 * Salesforce's, and shows here read-only — saving it is refused, and the form says where it is kept.
 * The status starts non-null, as the other forms here: a null Status renders as text.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class NoticeViewModel implements Identifiable, OptionsSupplier, io.mateu.uidl.interfaces.VisibilitySupplier {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "Nuevo");

    @ReadOnly
    @HiddenInCreate
    String id;

    // Strings with their options (OptionsSupplier) rather than enums: the options say it in the desk's
    // words, and a Set of an enum is drawn as a table, not as checkboxes.
    @Section("Sobre qué")
    @NotEmpty
    @EditableOnlyWhenCreating
    @Stereotype(FieldStereotype.select)
    @Label("Sobre")
    @Help("Una reserva (por su localizador del CRS) o una agencia (por su código del ERP). Los de cliente se "
            + "crean en Salesforce.")
    String subjectType = SubjectType.RESERVATION.name();

    @NotEmpty
    @EditableOnlyWhenCreating
    @Label("Localizador o código de agencia")
    String subjectId;

    @ReadOnly
    @HiddenInCreate
    @Label("Nombre")
    String subjectName;

    @Label("Hotel")
    @Help("Código del hotel en el CRS (p. ej. MRU01). Vacío: todos los hoteles de la cadena.")
    String hotelCode;

    @Section("Aviso")
    @NotEmpty
    @Multiline
    @Label("Texto")
    String text;

    @NotEmpty
    @Stereotype(FieldStereotype.select)
    @Label("Tipo")
    @Help("Uno bloqueante obliga a recepción a marcarlo como leído antes del check-in (o del check-out).")
    String type = NoticeType.INFORMATIVE.name();

    @Stereotype(FieldStereotype.checkbox)
    @Label("Cuándo lo ve recepción")
    @Help("Uno o varios momentos.")
    List<String> moments = new ArrayList<>(List.of(NoticeMoment.CHECK_IN.name()));

    @Label("Desde")
    LocalDate from;

    @Label("Hasta")
    LocalDate to;

    @Label("Activo")
    boolean active = true;

    @Section("Origen")
    @ReadOnly
    @HiddenInCreate
    @Label("Maestro")
    String source;

    @ReadOnly
    @HiddenInCreate
    @Label("Referencia en el maestro")
    String sourceRef;

    @ReadOnly
    @HiddenInCreate
    @Label("Versión")
    Long version;

    @ReadOnly
    @HiddenInCreate
    @Label("Último cambio")
    String updated;

    final Notices notices;

    public String create(HttpRequest httpRequest) {
        return notices.create(draft(), who(httpRequest)).id;
    }

    public void save(HttpRequest httpRequest) {
        notices.update(id, draft(), who(httpRequest));
    }

    @Toolbar
    @Action
    public Object desactivar(HttpRequest httpRequest) {
        load(notices.setActive(id, false, who(httpRequest)));
        return List.of(new Message("Aviso desactivado: recepción deja de verlo"), new State(this));
    }

    @Toolbar
    @Action
    public Object activar(HttpRequest httpRequest) {
        load(notices.setActive(id, true, who(httpRequest)));
        return List.of(new Message("Aviso activado"), new State(this));
    }

    Notices.Draft draft() {
        var set = EnumSet.noneOf(NoticeMoment.class);
        if (moments != null) {
            moments.stream().map(Notice::moment).filter(java.util.Objects::nonNull).forEach(set::add);
        }
        return new Notices.Draft(subjectType == null || subjectType.isBlank() ? null : SubjectType.valueOf(subjectType),
                subjectId, hotelCode, text, type == null || type.isBlank() ? null : NoticeType.valueOf(type), from, to,
                set, active);
    }

    /**
     * Deactivate an active notice, activate an inactive one — neither for a customer's (Salesforce's),
     * nor before it exists.
     */
    @Override
    public boolean isHidden(String memberName, HttpRequest httpRequest) {
        var ours = id != null && !SubjectType.CUSTOMER.name().equals(subjectType);
        return switch (memberName) {
            case "desactivar" -> !ours || !active;
            case "activar" -> !ours || active;
            default -> false;
        };
    }

    /** The choices, in the desk's words: what it is about, its type, when the desk sees it. */
    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "subjectType" -> List.of(new Option(SubjectType.RESERVATION.name(), "Reserva"),
                    new Option(SubjectType.PARTNER.name(), "Agencia"),
                    new Option(SubjectType.CUSTOMER.name(), "Cliente (Salesforce)"));
            case "type" -> List.of(new Option(NoticeType.INFORMATIVE.name(), "Informativo"),
                    new Option(NoticeType.IMPORTANT.name(), "Importante"),
                    new Option(NoticeType.BLOCKING.name(), "Bloqueante"));
            case "moments" -> List.of(new Option(NoticeMoment.PRE_ARRIVAL.name(), "Antes de la llegada"),
                    new Option(NoticeMoment.CHECK_IN.name(), "Check-in"),
                    new Option(NoticeMoment.IN_HOUSE.name(), "Durante la estancia"),
                    new Option(NoticeMoment.CHECK_OUT.name(), "Check-out"));
            default -> List.of();
        };
    }

    public NoticeViewModel load(Notice n) {
        id = n.id;
        status = n.active ? new Status(StatusType.SUCCESS, "Activo") : new Status(StatusType.NONE, "Inactivo");
        subjectType = n.subjectType;
        subjectId = n.subjectId;
        subjectName = n.subjectName;
        hotelCode = n.hotelCode;
        text = n.text;
        type = n.noticeType().name();
        moments = new ArrayList<>(n.momentSet().stream().sorted().map(Enum::name).toList());
        from = n.fromDate;
        to = n.toDate;
        active = n.active;
        source = n.salesforce() ? "Salesforce — se gestiona allí; aquí solo se lee" : "Avisos (este servicio)";
        sourceRef = n.sourceRef;
        version = n.version;
        updated = (n.updatedAt == null ? "" : WHEN.format(n.updatedAt)) + (n.updatedBy == null ? "" : " · " + n.updatedBy);
        return this;
    }

    /** Who is signed in, from the token the gateway let through; "consola" if it cannot be read. */
    static String who(HttpRequest httpRequest) {
        try {
            var header = httpRequest == null ? null : httpRequest.getHeaderValue("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                return "consola";
            }
            var parts = header.substring(7).split("\\.");
            var claims = new ObjectMapper().readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            var name = claims.path("name").asText(claims.path("preferred_username").asText(""));
            return name.isBlank() ? "consola" : name;
        } catch (RuntimeException | java.io.IOException e) {
            return "consola";
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nuevo aviso" : label(subjectType) + " " + subjectId + " · " + id;
    }

    static String label(String subjectType) {
        return subjectType == null ? "" : switch (SubjectType.valueOf(subjectType)) {
            case CUSTOMER -> "Cliente";
            case RESERVATION -> "Reserva";
            case PARTNER -> "Agencia";
        };
    }
}
