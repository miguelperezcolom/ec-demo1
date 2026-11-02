package io.mateu.ecdemo1.contenthub.application.calendar;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The newsletters' issues on the calendar: a scheduled one on when it goes out, a sent one on when it went.
 * Drafts and cancelled issues have no date. Type {@code NEWSLETTER}, the issue's status, no section; an
 * entry opens the issue (/editorial/numeros/{id}).
 */
@Component
@RequiredArgsConstructor
public class NewsletterIssuesCalendarSource implements CalendarSource {

    public static final String TYPE = "NEWSLETTER";
    public static final String SEND = "NEWSLETTER_SEND";
    public static final String SENT = "NEWSLETTER_SENT";

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    final NewsletterIssueRepository issues;
    final SiteRepository sites;

    @Override
    public List<CalendarEntry> entries(LocalDate from, LocalDate to) {
        var out = new ArrayList<CalendarEntry>();
        for (var issue : issues.findAll()) {
            var entry = of(issue, sites.find(issue.newsletterId()).map(Site::name).orElse(issue.newsletterId()));
            if (entry != null && !entry.date().isBefore(from) && !entry.date().isAfter(to)) {
                out.add(entry);
            }
        }
        return out;
    }

    static CalendarEntry of(NewsletterIssue issue, String newsletter) {
        Instant at;
        String kind;
        String label;
        String verb;
        String color;
        switch (issue.status()) {
            case SCHEDULED -> {
                at = issue.scheduledAt();
                kind = SEND;
                label = "Se envía";
                verb = "Envío";
                color = "#7c3aed";
            }
            case SENT -> {
                at = issue.sentAt();
                kind = SENT;
                label = "Enviado";
                verb = "Enviado";
                color = "#6b7280";
            }
            default -> {
                return null;
            }
        }
        if (at == null) {
            return null;
        }
        var local = LocalDateTime.ofInstant(at, ZONE);
        var subject = issue.subject() == null || issue.subject().isBlank() ? "" : " · " + issue.subject();
        return new CalendarEntry(kind + ":" + issue.id(), kind, label, local.toLocalDate(),
                local.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                verb + " · " + newsletter + " nº " + issue.number() + subject,
                "/editorial/numeros/" + issue.id(), color, TYPE, null, issue.status().name());
    }
}
