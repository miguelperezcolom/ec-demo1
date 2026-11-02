package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.template.PageTemplate;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository {

    Optional<PageTemplate> find(String id);

    void save(PageTemplate template);

    void delete(String id);

    /** By name. */
    List<PageTemplate> findAll();
}
