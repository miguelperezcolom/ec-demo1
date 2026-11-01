package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterEdition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NewsletterRepository {

    Optional<NewsletterEdition> find(String id);

    void save(NewsletterEdition edition);

    List<NewsletterEdition> findAll();

    List<NewsletterEdition> due(Instant now);
}
