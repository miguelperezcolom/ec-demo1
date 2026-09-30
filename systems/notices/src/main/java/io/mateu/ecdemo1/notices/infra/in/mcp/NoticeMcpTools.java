package io.mateu.ecdemo1.notices.infra.in.mcp;

import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeType;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.SubjectType;
import io.mateu.ecdemo1.notices.application.Notices;
import io.mateu.ecdemo1.notices.infra.in.rest.NoticeView;
import io.mateu.ecdemo1.notices.store.Notice;
import io.mateu.ecdemo1.notices.store.NoticeRepository;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The notices, for the data plane's agent: read them, and create, change or deactivate a reservation's
 * or a partner's. Every tool answers a list of views or a sentence — never Object, which Spring AI does
 * not offer as a tool at all.
 */
@Component
@Slf4j
public class NoticeMcpTools implements McpSystemContext {

    final Notices notices;
    final NoticeRepository repository;

    public NoticeMcpTools(Notices notices, NoticeRepository repository) {
        this.notices = notices;
        this.repository = repository;
    }

    @Override
    public String getSystemContext() {
        return """
                Avisos de recepción:
                - Un aviso es lo que recepción debe saber de un cliente (CUSTOMER), de una reserva
                  (RESERVATION, por su localizador del CRS) o de una agencia (PARTNER, por su código del ERP).
                - Se ven en uno o varios momentos: PRE_ARRIVAL (al preparar la llegada), CHECK_IN, IN_HOUSE
                  (durante la estancia) y CHECK_OUT. Pueden limitarse a un hotel (código del CRS, p. ej.
                  MRU01) y a unas fechas; sin hotel valen para toda la cadena.
                - Tipo: INFORMATIVE, IMPORTANT o BLOCKING. Uno BLOCKING obliga a recepción a marcarlo como
                  leído antes del check-in (o del check-out).
                - Los avisos de cliente son de Salesforce, su maestro: aquí solo se leen. Los de reserva y
                  agencia se crean y se cambian aquí.
                """;
    }

    @Tool(description = "List reception notices, newest first. subjectType: CUSTOMER, RESERVATION or PARTNER "
            + "(empty, any); subjectId: the locator, partner code or customer code (empty, any)")
    public List<NoticeView> listNotices(@ToolParam(required = false) String subjectType,
                                        @ToolParam(required = false) String subjectId) {
        var page = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return repository.findAll(page).stream()
                .filter(n -> blank(subjectType) || n.subjectType.equalsIgnoreCase(subjectType.trim()))
                .filter(n -> blank(subjectId) || n.subjectId.equalsIgnoreCase(subjectId.trim()))
                .map(NoticeView::of).toList();
    }

    @Tool(description = "Create a notice on a reservation, by its CRS locator. type: INFORMATIVE, IMPORTANT or "
            + "BLOCKING; moments: some of PRE_ARRIVAL, CHECK_IN, IN_HOUSE, CHECK_OUT; hotelCode, from and to "
            + "(yyyy-MM-dd) are optional")
    public String createReservationNotice(String locator, String text, String type, List<String> moments,
                                          @ToolParam(required = false) String hotelCode,
                                          @ToolParam(required = false) String from,
                                          @ToolParam(required = false) String to) {
        return create(SubjectType.RESERVATION, locator, text, type, moments, hotelCode, from, to);
    }

    @Tool(description = "Create a notice on a partner (agency, tour operator, company), by its code in the ERP. "
            + "type: INFORMATIVE, IMPORTANT or BLOCKING; moments: some of PRE_ARRIVAL, CHECK_IN, IN_HOUSE, "
            + "CHECK_OUT; hotelCode, from and to (yyyy-MM-dd) are optional")
    public String createPartnerNotice(String partnerCode, String text, String type, List<String> moments,
                                      @ToolParam(required = false) String hotelCode,
                                      @ToolParam(required = false) String from,
                                      @ToolParam(required = false) String to) {
        return create(SubjectType.PARTNER, partnerCode, text, type, moments, hotelCode, from, to);
    }

    @Tool(description = "Deactivate a reservation's or a partner's notice by its id: the desk stops seeing it")
    public String deactivateNotice(String noticeId) {
        log.info("MCP deactivateNotice {}", noticeId);
        return attempt(() -> "Aviso %s desactivado".formatted(notices.setActive(noticeId, false, "agente").id));
    }

    String create(SubjectType subject, String id, String text, String type, List<String> moments, String hotelCode,
                  String from, String to) {
        log.info("MCP create notice on {} {}", subject, id);
        return attempt(() -> {
            var set = EnumSet.noneOf(NoticeMoment.class);
            if (moments != null) {
                moments.forEach(m -> {
                    var moment = Notice.moment(m.trim().toUpperCase(Locale.ROOT));
                    if (moment == null) {
                        throw new IllegalArgumentException("Momento desconocido: " + m);
                    }
                    set.add(moment);
                });
            }
            var created = notices.create(new Notices.Draft(subject, id, hotelCode, text,
                    blank(type) ? NoticeType.INFORMATIVE : NoticeType.valueOf(type.trim().toUpperCase(Locale.ROOT)),
                    blank(from) ? null : LocalDate.parse(from.trim()), blank(to) ? null : LocalDate.parse(to.trim()),
                    set, true), "agente");
            return "Aviso %s creado en %s %s".formatted(created.id, subject, created.subjectId);
        });
    }

    static String attempt(Supplier<String> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            return "No se ha hecho: " + e.getMessage();
        }
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
