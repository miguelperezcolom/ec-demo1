package io.mateu.ecdemo1.booking.infra.out.persistence;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingCriteria;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingDBQueryService implements BookingQueryService {

    final BookingEntityRepository repository;

    /** Most recent first unless the page asks for another order. */
    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "created");

    @Override
    public Page<BookingDto> findAll(String searchText, BookingCriteria criteria, Pageable pageable) {
        return repository.findAll(matching(searchText, criteria), ordered(pageable))
                .map(BookingMapper::toDomain).map(this::toDto);
    }

    static Pageable ordered(Pageable pageable) {
        return pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    }

    /** The listing's query: the free text as {@link BookingEntityRepository#search}, and the criteria. */
    static Specification<BookingEntity> matching(String text, BookingCriteria criteria) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            if (text != null && !text.isBlank()) {
                var like = "%" + text.trim().toLowerCase() + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("id")), like),
                        cb.like(cb.lower(root.get("holderName")), like),
                        cb.like(cb.lower(root.get("hotelCode")), like)));
            }
            if (criteria != null) {
                if (criteria.hotelCode() != null) {
                    where.add(cb.equal(root.get("hotelCode"), criteria.hotelCode()));
                }
                if (criteria.ids() != null && !criteria.ids().isEmpty()) {
                    where.add(root.get("id").in(criteria.ids()));
                }
                if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
                    where.add(root.get("status").in(criteria.statuses().stream().map(Enum::name).toList()));
                }
                between(root.get("arrival"), criteria.arrivalFrom(), criteria.arrivalTo(), cb, where);
                between(root.get("departure"), criteria.departureFrom(), criteria.departureTo(), cb, where);
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static void between(Path<LocalDate> date, LocalDate from, LocalDate to, CriteriaBuilder cb,
                                List<Predicate> where) {
        if (from != null) {
            where.add(cb.greaterThanOrEqualTo(date, from));
        }
        if (to != null) {
            where.add(cb.lessThanOrEqualTo(date, to));
        }
    }

    @Override
    public List<BookingDto> list(String text, int page, int size) {
        return repository.search(text, PageRequest.of(page, size))
                .map(BookingMapper::toDomain).map(this::toDto).getContent();
    }

    @Override
    public List<BookingDto> future(String hotelCode, LocalDate from, LocalDate afterArrival, String afterId, int limit) {
        var start = afterArrival == null ? from.minusDays(1) : afterArrival;
        return repository.future(hotelCode, from, start, afterId == null ? "" : afterId,
                        PageRequest.of(0, limit)).stream()
                .map(BookingMapper::toDomain).map(this::toDto).toList();
    }

    @Override
    public String getLabel(String id) {
        return repository.findById(id).map(BookingEntity::getHolderName).orElse("Unknown");
    }

    @Override
    public Optional<BookingDto> getById(String id) {
        return repository.findById(id).map(BookingMapper::toDomain).map(this::toDto);
    }

    private BookingDto toDto(Booking booking) {
        var terms = booking.getTerms();
        return new BookingDto(
                booking.getId().id(),
                booking.getHotelCode(),
                booking.getCurrency(),
                booking.getStatus(),
                booking.getVersion(),
                terms.channelCode(),
                terms.partnerCode(),
                terms.externalReference(),
                terms.stay().arrival(),
                terms.stay().departure(),
                terms.stay().nights(),
                terms.holder(),
                terms.rooms(),
                booking.getPayments(),
                booking.totalAmount(),
                booking.paidAmount(),
                terms.comments(),
                booking.getCancellation(),
                booking.getPmsReference(),
                booking.getCreated(),
                booking.getUpdated(),
                booking.originalAmount());
    }

}
