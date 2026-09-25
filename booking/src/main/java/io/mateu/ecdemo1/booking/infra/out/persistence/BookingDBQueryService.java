package io.mateu.ecdemo1.booking.infra.out.persistence;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingRow;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingDBQueryService implements BookingQueryService {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    final BookingEntityRepository repository;

    @Override
    public ListingData<BookingRow> findAll(String searchText, Object filters, Pageable pageable) {
        var page = repository.search(searchText, org.springframework.data.domain.PageRequest
                .of(pageable.page(), pageable.size()));
        return new ListingData<>(new Page<>(searchText, page.getSize(), page.getNumber(), page.getTotalElements(),
                page.getContent().stream().map(BookingMapper::toDomain).map(this::toRow).toList()));
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
                booking.getHotelCode(),
                terms.holder().fullName(),
                terms.stay().arrival().format(DATE),
                terms.stay().departure().format(DATE),
                booking.totalAmount().toPlainString() + " " + booking.getCurrency(),
                status(booking),
                booking.getVersion(),
                booking.getPmsReference() != null ? booking.getPmsReference().reservationId() : null);
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
