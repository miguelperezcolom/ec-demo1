package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The issues of the newsletters. One per number and newsletter, and per cadence slot (the store enforces both). */
public interface NewsletterIssueRepository {

    Optional<NewsletterIssue> find(String id);

    void save(NewsletterIssue issue);

    /** A newsletter's issues, the latest number first. */
    List<NewsletterIssue> ofNewsletter(String newsletterId);

    List<NewsletterIssue> findAll();

    List<NewsletterIssue> due(Instant now);

    /** The issue with the highest number below {@code number}: the previous one. */
    default Optional<NewsletterIssue> previous(String newsletterId, int number) {
        return ofNewsletter(newsletterId).stream().filter(i -> i.number() < number).findFirst();
    }

    /** The latest issue of a newsletter, whatever its status. */
    default Optional<NewsletterIssue> latest(String newsletterId) {
        return ofNewsletter(newsletterId).stream().findFirst();
    }
}
