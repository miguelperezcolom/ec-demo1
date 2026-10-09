package io.mateu.ecdemo1.loyalty.infra.in.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.loyalty.application.Loyalty;
import io.mateu.ecdemo1.loyalty.store.Member;
import io.mateu.ecdemo1.loyalty.store.MemberRepository;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Socios: the Riu Class members, filtered, ordered and paged by the database, most recently changed
 * first. A member opens with its data and what each stay earned it; its tier and points can be changed
 * by hand (it is a demo), and a member created.
 */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Socios")
public class MemberCrud extends Crud<MemberViewModel, MemberViewModel, MemberViewModel, MemberFilters, MemberRow, String> {

    final MemberViewModel viewModel;
    final MemberRepository repository;
    final Loyalty loyalty;

    @Override
    public ListingData<MemberRow> search(SearchRequest request, HttpRequest httpRequest) {
        var filters = filters(request);
        var size = Math.max(1, request.pageable() == null ? 20 : request.pageable().size());
        var number = Math.max(0, request.pageable() == null ? 0 : request.pageable().page());
        var found = repository.findAll(spec(request.searchText(), filters),
                PageRequest.of(number, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        var rows = found.getContent().stream().map(MemberCrud::row).toList();
        return new ListingData<>(new Page<>(request.searchText(), size, number, found.getTotalElements(), rows));
    }

    static Specification<Member> spec(String text, MemberFilters filters) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (text != null && !text.isBlank()) {
                var like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("memberNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("customerCode"), "")), like)));
            }
            if (filters != null && filters.tier != null && !filters.tier.isEmpty()) {
                predicates.add(root.get("tier").in(filters.tier.stream().map(Enum::name).toList()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static MemberRow row(Member m) {
        return new MemberRow(m.memberNumber, m.customerCode == null ? "" : m.customerCode, Formats.tier(m.tier()),
                Formats.points(m.points), Formats.day(m.memberSince), Formats.when(m.updatedAt));
    }

    @Override
    public MemberViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(loyalty.get(id));
    }

    @Override
    public MemberViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(loyalty.get(id));
    }

    @Override
    public MemberViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        return httpRequest.getComponentState(MemberViewModel.class).save();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(MemberViewModel.class).create();
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new IllegalStateException("Un socio no se borra desde aquí: la demo los vuelve a sembrar en cada reset");
    }

    @Override
    public String getIdFieldForRow() {
        return "memberNumber";
    }
}
