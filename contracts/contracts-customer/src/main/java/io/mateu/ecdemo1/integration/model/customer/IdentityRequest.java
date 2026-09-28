package io.mateu.ecdemo1.integration.model.customer;

import io.mateu.ecdemo1.integration.model.reservation.Person;

import java.util.List;

/**
 * Who the passengers of a reservation are, asked of the customer MDM when the reservation is about
 * to reach the PMS (HLA CRM-MDM, F001). The reservation is the source: asking again for the same one
 * returns the same customers, so a retried step creates nothing twice.
 *
 * @param passengers the holder first, then the guests of each room in order
 */
public record IdentityRequest(String hotelCode, String locator, List<Person> passengers) {
}
