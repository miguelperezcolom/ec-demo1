package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgNewsletterIssueRepository implements NewsletterIssueRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<NewsletterIssue> find(String id) {
        return jdbc.query("select doc from ch_nl_issue where id = ?", (rs, i) -> read(rs.getString(1)), id).stream()
                .findFirst();
    }

    /** By id: a second save of the same issue updates it; another issue with its number or slot is refused. */
    @Override
    public void save(NewsletterIssue issue) {
        jdbc.update("insert into ch_nl_issue (id, newsletter_id, number, status, scheduled_at, slot, doc) "
                        + "values (?, ?, ?, ?, ?, ?, ?::jsonb) on conflict (id) do update set status = excluded.status, "
                        + "scheduled_at = excluded.scheduled_at, doc = excluded.doc",
                issue.id(), issue.newsletterId(), issue.number(), issue.status().name(), Sql.ts(issue.scheduledAt()),
                Sql.ts(issue.slot()), Json.write(issue));
    }

    @Override
    public List<NewsletterIssue> ofNewsletter(String newsletterId) {
        return jdbc.query("select doc from ch_nl_issue where newsletter_id = ? order by number desc",
                (rs, i) -> read(rs.getString(1)), newsletterId);
    }

    @Override
    public List<NewsletterIssue> findAll() {
        return jdbc.query("select doc from ch_nl_issue order by newsletter_id, number desc", (rs, i) -> read(rs.getString(1)));
    }

    @Override
    public List<NewsletterIssue> due(Instant now) {
        return jdbc.query("select doc from ch_nl_issue where status = 'SCHEDULED' and scheduled_at <= ? order by scheduled_at",
                (rs, i) -> read(rs.getString(1)), Sql.ts(now));
    }

    static NewsletterIssue read(String json) {
        return Json.read(json, NewsletterIssue.class);
    }
}
