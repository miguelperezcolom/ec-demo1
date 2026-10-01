package io.mateu.ecdemo1.notices.infra.in.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeType;
import io.mateu.ecdemo1.notices.application.Notices;
import io.mateu.ecdemo1.notices.store.Notice;
import io.mateu.ecdemo1.notices.store.NoticeRepository;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The reception notices: a reservation's and a partner's, created and changed here, and the customers'
 * as Salesforce has them (read-only). Filtered, ordered and paged by the database, newest change first.
 */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Avisos de recepción")
public class NoticeCrud extends Crud<NoticeViewModel, NoticeViewModel, NoticeViewModel, NoticeFilters, NoticeRow, String> {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    final NoticeViewModel viewModel;
    final NoticeRepository repository;
    final Notices notices;

    @Override
    public ListingData<NoticeRow> search(SearchRequest request, HttpRequest httpRequest) {
        var filters = filters(request);
        var size = Math.max(1, request.pageable().size());
        var number = Math.max(0, request.pageable().page());
        var found = repository.findAll(spec(request.searchText(), filters),
                PageRequest.of(number, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        var rows = found.getContent().stream().map(NoticeCrud::row).toList();
        return new ListingData<>(new Page<>(request.searchText(), size, number, found.getTotalElements(), rows));
    }

    static Specification<Notice> spec(String text, NoticeFilters filters) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (text != null && !text.isBlank()) {
                var like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("text")), like),
                        cb.like(cb.lower(root.get("subjectId")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("subjectName"), "")), like)));
            }
            if (filters != null) {
                if (filters.subject != null && !filters.subject.isEmpty()) {
                    predicates.add(root.get("subjectType").in(names(filters.subject)));
                }
                if (filters.type != null && !filters.type.isEmpty()) {
                    predicates.add(root.get("type").in(names(filters.type)));
                }
                if (filters.status != null && filters.status.size() == 1) {
                    predicates.add(cb.equal(root.get("active"), filters.status.contains(NoticeFilters.State.Activo)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static List<String> names(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).toList();
    }

    static NoticeRow row(Notice n) {
        return new NoticeRow(n.id, sobre(n), n.hotelCode == null ? "Cadena" : n.hotelCode, corto(n.text), tipo(n.noticeType()),
                n.momentSet().stream().sorted().map(NoticeCrud::momento).collect(Collectors.joining(", ")),
                vigencia(n),
                n.active ? new Status(StatusType.SUCCESS, "Activo") : new Status(StatusType.NONE, "Inactivo"),
                n.salesforce() ? "Salesforce" : "Avisos",
                n.text);
    }

    /** The notice on one line for the listing: about 60 characters, cut at a word; the whole of it opens under the row. */
    static String corto(String text) {
        if (text == null) {
            return "";
        }
        var oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= 60) {
            return oneLine;
        }
        var cut = oneLine.lastIndexOf(' ', 60);
        return oneLine.substring(0, cut > 30 ? cut : 60) + "…";
    }

    static String sobre(Notice n) {
        var s = n.subject();
        var label = switch (s) {
            case CUSTOMER -> "Cliente ";
            case RESERVATION -> "Reserva ";
            case PARTNER -> "Agencia ";
        };
        return label + n.subjectId + (n.subjectName == null ? "" : " · " + n.subjectName);
    }

    static Status tipo(NoticeType type) {
        return switch (type) {
            case BLOCKING -> new Status(StatusType.DANGER, "Bloqueante");
            case IMPORTANT -> new Status(StatusType.WARNING, "Importante");
            case INFORMATIVE -> new Status(StatusType.INFO, "Informativo");
        };
    }

    static String momento(NoticeMoment m) {
        return switch (m) {
            case PRE_ARRIVAL -> "Antes de la llegada";
            case CHECK_IN -> "Check-in";
            case IN_HOUSE -> "Estancia";
            case CHECK_OUT -> "Check-out";
        };
    }

    static String vigencia(Notice n) {
        if (n.fromDate == null && n.toDate == null) {
            return "Siempre";
        }
        return (n.fromDate == null ? "…" : DAY.format(n.fromDate)) + " → " + (n.toDate == null ? "…" : DAY.format(n.toDate));
    }

    @Override
    public NoticeViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(notices.find(id));
    }

    @Override
    public NoticeViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(notices.find(id));
    }

    @Override
    public NoticeViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var editor = httpRequest.getComponentState(NoticeViewModel.class);
        editor.save(httpRequest);
        return editor.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(NoticeViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new IllegalStateException("Un aviso no se borra: se desactiva, y recepción deja de verlo");
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
