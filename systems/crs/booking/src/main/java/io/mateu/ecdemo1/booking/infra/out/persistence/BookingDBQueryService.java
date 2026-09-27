package io.mateu.ecdemo1.booking.infra.out.persistence;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingCriteria;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingRow;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingDBQueryService implements BookingQueryService {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    final BookingEntityRepository repository;
    final CrsCatalog catalog;

    @Override
    public ListingData<BookingRow> findAll(String searchText, Object filters, Pageable pageable) {
        var page = repository.findAll(matching(searchText, filters instanceof BookingCriteria c ? c : null),
                PageRequest.of(pageable.page(), pageable.size(), Sort.by(Sort.Direction.DESC, "created")));
        return new ListingData<>(new Page<>(searchText, page.getSize(), page.getNumber(), page.getTotalElements(),
                page.getContent().stream().map(BookingMapper::toDomain).map(this::toRow).toList()));
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
        return repository.search(text, org.springframework.data.domain.PageRequest.of(page, size))
                .map(BookingMapper::toDomain).map(this::toDto).getContent();
    }

    @Override
    public List<BookingDto> future(String hotelCode, LocalDate from, LocalDate afterArrival, String afterId, int limit) {
        var start = afterArrival == null ? from.minusDays(1) : afterArrival;
        return repository.future(hotelCode, from, start, afterId == null ? "" : afterId,
                        org.springframework.data.domain.PageRequest.of(0, limit)).stream()
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

    private BookingRow toRow(Booking booking) {
        var terms = booking.getTerms();
        return new BookingRow(
                booking.getId().id(),
                hotelName(booking.getHotelCode()),
                terms.holder().fullName(),
                terms.stay().arrival().format(DATE),
                terms.stay().departure().format(DATE),
                booking.totalAmount().toPlainString() + " " + booking.getCurrency(),
                status(booking),
                booking.getVersion(),
                booking.getPmsReference() != null ? booking.getPmsReference().reservationId() : null);
    }

    /** A person reads a hotel by its name; the code stays for a hotel the catalog no longer has. */
    String hotelName(String code) {
        return catalog.hotels().stream().filter(h -> h.code().equals(code)).map(CrsCatalog.Hotel::name)
                .findFirst().orElse(code);
    }

    static Status status(Booking booking) {
        return new Status(switch (booking.getStatus()) {
            case Pending -> StatusType.INFO;
            case Confirmed -> StatusType.SUCCESS;
            case Cancelled -> StatusType.DANGER;
        }, booking.getStatus().name());
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
