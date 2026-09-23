package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2GuestRepository}. */
interface GuestCrud extends ListCrudRepository<Guest, String> {}
