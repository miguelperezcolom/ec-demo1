package io.mateu.ecdemo1.booking.application.usecases.booking.update;

import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateBookingUseCase {

    final BookingRepository repository;

    @Transactional
    public void handle(UpdateBookingCommand command) {
        var resource = repository.findById(new BookingId(command.id())).orElseThrow();
        resource.update(new Name(command.name()));
        repository.save(resource);
    }

}
