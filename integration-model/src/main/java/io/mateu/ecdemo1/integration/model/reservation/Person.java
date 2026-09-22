package io.mateu.ecdemo1.integration.model.reservation;

import java.time.LocalDate;

/** The holder of a reservation, or a guest in one of its rooms. */
public record Person(String firstName,
                     String lastName,
                     GuestType type,
                     Integer age,
                     String email,
                     String phone,
                     String nationality,
                     LocalDate birthDate,
                     String documentType,
                     String documentNumber) {
}
