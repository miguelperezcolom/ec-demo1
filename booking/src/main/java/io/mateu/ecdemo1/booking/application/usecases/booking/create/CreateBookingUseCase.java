package io.mateu.ecdemo1.booking.application.usecases.booking.create;

import io.mateu.core.infra.valuegenerators.LocatorValueGenerator;
import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.aggregates.shared.vo.Name;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateBookingUseCase {

    final BookingRepository repository;
    final StreamBridge streamBridge;
    final LocatorValueGenerator locatorValueGenerator;

    @Transactional
    public String handle(CreateBookingCommand command) {
        var id = repository.save(Booking.of(
                new BookingId(locatorValueGenerator.generate().toString()),
                new Name(command.leadName())
        )).id();
        streamBridge.send("upstream", new ProcessCreationRequested(
                "verify-booking-payment",
                "verify-payment-for-" + id,
                List.of(
                        new Variable("bookingId", id)
                )));
        return id;
    }

}
