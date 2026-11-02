package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;

import java.util.List;
import java.util.Optional;

/** The newsletters — what makes a site a newsletter —, by their site's id. */
public interface NewsletterRepository {

    Optional<Newsletter> find(String siteId);

    void save(Newsletter newsletter);

    List<Newsletter> findAll();
}
