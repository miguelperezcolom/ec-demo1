package io.mateu.ecdemo1.booking.domain.aggregates.booking.vo;

import java.time.LocalDate;

/**
 * Someone staying in a room. Only the names and the type are required: a CRS often knows no more
 * than that until check-in.
 */
public record Guest(String firstName,
                    String lastName,
                    GuestType type,
                    Integer age,
                    LocalDate birthDate,
                    String nationality,
                    String documentType,
                    String documentNumber) {

    public Guest {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("A guest needs a first name and a last name");
        }
        if (type == null) {
            throw new IllegalArgumentException("Guest %s %s needs a type".formatted(firstName, lastName));
        }
        if (type == GuestType.Child && age == null) {
            throw new IllegalArgumentException("Child %s %s needs an age".formatted(firstName, lastName));
        }
    }
}
