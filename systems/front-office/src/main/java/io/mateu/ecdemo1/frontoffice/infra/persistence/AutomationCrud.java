package io.mateu.ecdemo1.frontoffice.infra.persistence;

import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2AutomationRepository}. */
interface AutomationCrud extends ListCrudRepository<AutomationEntity, String> {}
