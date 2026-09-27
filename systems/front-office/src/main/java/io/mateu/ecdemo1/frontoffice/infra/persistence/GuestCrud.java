package io.mateu.ecdemo1.frontoffice.infra.persistence;

import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2GuestRepository}. */
interface GuestCrud extends ListCrudRepository<GuestEntity, String> {}
