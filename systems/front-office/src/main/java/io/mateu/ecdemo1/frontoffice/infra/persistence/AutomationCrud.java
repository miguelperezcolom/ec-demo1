package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.automation.Automation;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository backing {@link H2AutomationRepository}. */
interface AutomationCrud extends ListCrudRepository<Automation, String> {}
