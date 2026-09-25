package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.GuestType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * A guest of the booking form. The booking keeps guests inside their room; the form lists them
 * flat, with the room's line, which keeps it one list deep.
 */
public record GuestViewModel(
        @Min(1) int roomLine,
        @NotEmpty String firstName,
        @NotEmpty String lastName,
        @NotNull GuestType type,
        Integer age,
        LocalDate birthDate,
        String nationality,
        String documentType,
        String documentNumber
) {
}
