package io.mateu.ecdemo1.registration.infra.in.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.registration.application.RegistrationRules;
import io.mateu.ecdemo1.registration.store.RegistrationRule;
import io.mateu.ecdemo1.registration.store.RegistrationRuleRepository;
import io.mateu.uidl.annotations.PageWidth;
import io.mateu.uidl.annotations.PageWidthStyle;
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
import java.util.stream.Collectors;

/**
 * The registration rules: created, changed, deactivated here — never deleted, so what applied on a day
 * can still be told. Filtered, ordered and paged by the database, by scope.
 */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Reglas de registro")
@PageWidth(PageWidthStyle.EDGE_TO_EDGE)
public class RuleCrud extends Crud<RuleViewModel, RuleViewModel, RuleViewModel, RuleFilters, RuleRow, String> {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    final RuleViewModel viewModel;
    final RegistrationRuleRepository repository;
    final RegistrationRules rules;

    @Override
    public ListingData<RuleRow> search(SearchRequest request, HttpRequest httpRequest) {
        var filters = filters(request);
        var size = Math.max(1, request.pageable().size());
        var number = Math.max(0, request.pageable().page());
        var found = repository.findAll(spec(request.searchText(), filters),
                PageRequest.of(number, size, Sort.by("scope", "scopeCode", "name")));
        var rows = found.getContent().stream().map(RuleCrud::row).toList();
        return new ListingData<>(new Page<>(request.searchText(), size, number, found.getTotalElements(), rows));
    }

    static Specification<RegistrationRule> spec(String text, RuleFilters filters) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (text != null && !text.isBlank()) {
                var like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("scopeCode")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("legalBasis"), "")), like)));
            }
            if (filters != null) {
                if (filters.scope != null && !filters.scope.isEmpty()) {
                    predicates.add(root.get("scope").in(filters.scope.stream().map(Enum::name).toList()));
                }
                if (filters.status != null && filters.status.size() == 1) {
                    predicates.add(cb.equal(root.get("active"), filters.status.contains(RuleFilters.State.Activa)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static RuleRow row(RegistrationRule r) {
        return new RuleRow(r.id, r.name, Labels.scope(r.scope()) + " " + r.scopeCode, aQuien(r),
                r.requiredList().stream().map(Labels::field).collect(Collectors.joining(", "))
                        + (r.exemptList().isEmpty() ? "" : " · exime: "
                        + r.exemptList().stream().map(Labels::field).collect(Collectors.joining(", "))),
                r.momentList().stream().map(Labels::moment).collect(Collectors.joining(", ")),
                r.legalBasis == null ? "" : r.legalBasis, vigencia(r),
                r.active ? new Status(StatusType.SUCCESS, "Activa") : new Status(StatusType.NONE, "Inactiva"));
    }

    /** «El titular · no UE · 14+ años». */
    static String aQuien(RegistrationRule r) {
        var parts = new ArrayList<String>();
        parts.add(Labels.role(r.role()));
        switch (r.nationalityMatch()) {
            case IN -> parts.add("de " + String.join(", ", r.nationalityList()));
            case NOT_IN -> parts.add("salvo " + String.join(", ", r.nationalityList()));
            default -> { }
        }
        if (r.minAge != null || r.maxAge != null) {
            parts.add(r.minAge != null && r.maxAge != null ? r.minAge + "–" + r.maxAge + " años"
                    : r.minAge != null ? r.minAge + "+ años" : "hasta " + r.maxAge + " años");
        }
        return String.join(" · ", parts);
    }

    static String vigencia(RegistrationRule r) {
        if (r.fromDate == null && r.toDate == null) {
            return "Siempre";
        }
        return (r.fromDate == null ? "…" : DAY.format(r.fromDate)) + " → " + (r.toDate == null ? "…" : DAY.format(r.toDate));
    }

    @Override
    public RuleViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(rules.find(id));
    }

    @Override
    public RuleViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(rules.find(id));
    }

    @Override
    public RuleViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var editor = httpRequest.getComponentState(RuleViewModel.class);
        editor.save(httpRequest);
        return editor.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(RuleViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new IllegalStateException("Una regla no se borra: se desactiva, y deja de aplicarse");
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
