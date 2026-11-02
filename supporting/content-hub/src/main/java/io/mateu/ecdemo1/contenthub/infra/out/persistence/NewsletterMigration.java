package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterEditorial;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.navigation.Section;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Cadence;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The newsletter as it was — editions, each with everything (ch_newsletter), and one preference per
 * employee (ch_newsletter_pref) — into newsletters as sites: the editions become the issues of one
 * newsletter, «Boletín de la intranet» (its audience the most recent edition's; weekly on Mondays at 9 if
 * the weekly newsletter was on), numbered by date; each keeps its id, so its opens and clicks stay with it;
 * each preference becomes that newsletter's subscription. The old tables are left as they were.
 *
 * <p>Idempotent, at every start: an edition already brought is not brought again, a subscription already
 * there is not touched.
 */
@Component
@Slf4j
public class NewsletterMigration {

    public static final String BULLETIN = "boletin-intranet";
    public static final String BULLETIN_NAME = "Boletín de la intranet";
    /** The one section of the bulletin: the editions had their items in a single list. */
    public static final String HIGHLIGHTS = "destacados";

    final JdbcTemplate jdbc;
    final NewsletterEditorial editorial;
    final NewsletterRepository newsletters;
    final NewsletterIssueRepository issues;
    final NavigationRepository navigation;
    final TransactionTemplate transactions;
    final Clock clock;
    final boolean weekly;
    final boolean weeklySend;

    public NewsletterMigration(JdbcTemplate jdbc, NewsletterEditorial editorial, NewsletterRepository newsletters,
                               NewsletterIssueRepository issues, NavigationRepository navigation,
                               TransactionTemplate transactions, Clock clock,
                               @Value("${content-hub.newsletter.weekly.enabled:true}") boolean weekly,
                               @Value("${content-hub.newsletter.weekly.send:false}") boolean weeklySend) {
        this.jdbc = jdbc;
        this.editorial = editorial;
        this.newsletters = newsletters;
        this.issues = issues;
        this.navigation = navigation;
        this.transactions = transactions;
        this.clock = clock;
        this.weekly = weekly;
        this.weeklySend = weeklySend;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public void start() {
        try {
            var r = transactions.execute(s -> migrate());
            if (r != null && (r.issues() > 0 || r.subscriptions() > 0)) {
                log.info("Newsletter migrated: {} edition(s) into issues of «{}», {} subscription(s)", r.issues(),
                        BULLETIN_NAME, r.subscriptions());
            }
        } catch (RuntimeException e) {
            log.error("Could not migrate the newsletter's editions", e);
        }
    }

    public record Result(int issues, int subscriptions) {
    }

    record Legacy(String id, JsonNode doc, String status, Instant scheduledAt) {
    }

    /** Brings what was not brought yet. */
    public Result migrate() {
        var legacy = jdbc.query("select id, doc, status, scheduled_at from ch_newsletter "
                        + "order by scheduled_at asc nulls last, id",
                (rs, i) -> new Legacy(rs.getString(1), read(rs.getString(2)), rs.getString(3),
                        Sql.instant(rs.getTimestamp(4))));
        var prefs = jdbc.queryForObject("select count(*) from ch_newsletter_pref", Long.class);
        if (legacy.isEmpty() && (prefs == null || prefs == 0)) {
            return new Result(0, 0);
        }
        var now = clock.instant();
        if (newsletters.find(BULLETIN).isEmpty()) {
            createBulletin(legacy);
        }
        var newsletter = newsletters.find(BULLETIN).orElseThrow();
        var brought = 0;
        for (var e : legacy) {
            if (issues.find(e.id()).isPresent()) {
                continue;
            }
            var slot = slotOf(e.id());
            if (slot != null && issues.ofNewsletter(BULLETIN).stream().anyMatch(i -> slot.equals(i.slot()))) {
                continue;
            }
            issues.save(issue(e, newsletter.nextNumber(), slot, now));
            newsletter.spend(slot);
            brought++;
        }
        // the week that has just passed had its edition already, or will be opened by the scheduler — never twice
        newsletters.save(newsletter);
        var subscriptions = jdbc.update("insert into ch_nl_subscription (user_id, newsletter_id, subscribed, sections) "
                + "select user_id, ?, subscribed, '{}'::text[] from ch_newsletter_pref on conflict do nothing", BULLETIN);
        return new Result(brought, subscriptions);
    }

    /** The bulletin: the most recent edition's audience, one section with every intranet section as its source. */
    void createBulletin(List<Legacy> legacy) {
        Audience audience = Audience.EVERYONE;
        for (int i = legacy.size() - 1; i >= 0; i--) {
            var a = legacy.get(i).doc().get("audience");
            if (a != null && !a.isNull()) {
                audience = Json.MAPPER.convertValue(a, Audience.class);
                break;
            }
        }
        var sources = navigation.get().sections().stream().filter(s -> s.parentId() == null).map(Section::id).toList();
        var cadence = weekly ? Cadence.weekly(DayOfWeek.MONDAY, LocalTime.of(9, 0)) : Cadence.NONE;
        editorial.createWithId(BULLETIN, new Site.Definition(BULLETIN_NAME,
                        "Lo que se ha publicado en la intranet, cada semana.", audience, List.of("es"), null,
                        Site.Theme.DEFAULT, "", "", true),
                List.of(new NewsletterEditorial.SectionSpec(HIGHLIGHTS, "Lo más destacado", sources)),
                new Newsletter.Settings(List.of(), Map.of(), cadence, weeklySend));
    }

    NewsletterIssue issue(Legacy e, int number, Instant slot, Instant now) {
        var doc = e.doc();
        var title = text(doc, "title", "Boletín");
        var intro = text(doc, "intro", "");
        var itemIds = new ArrayList<String>();
        if (doc.get("itemIds") != null) {
            doc.get("itemIds").forEach(n -> itemIds.add(n.asText()));
        }
        var blocks = new ArrayList<Block>();
        if (!intro.isBlank()) {
            blocks.add(new Block(Newsletter.INTRO_BLOCK, BlockType.HTML, null, "",
                    intro.contains("<") ? intro : "<p>" + intro + "</p>", null, null, null, null, null));
        }
        blocks.add(Newsletter.sectionBlock("s-" + HIGHLIGHTS, HIGHLIGHTS, ""));
        var sends = new ArrayList<NewsletterIssue.GroupSend>();
        Instant lastSent = null;
        if (doc.get("sends") != null) {
            for (var s : doc.get("sends")) {
                var send = Json.MAPPER.convertValue(s, NewsletterIssue.GroupSend.class);
                sends.add(send);
                if (send.sentAt() != null && (lastSent == null || send.sentAt().isAfter(lastSent))) {
                    lastSent = send.sentAt();
                }
            }
        }
        var status = switch (e.status() == null ? "DRAFT" : e.status()) {
            case "SENT" -> NewsletterIssue.Status.SENT;
            case "SCHEDULED" -> NewsletterIssue.Status.SCHEDULED;
            default -> NewsletterIssue.Status.DRAFT;
        };
        var when = e.scheduledAt() != null ? e.scheduledAt() : slot != null ? slot : now;
        return NewsletterIssue.imported(e.id(), BULLETIN, number, LocalDate.ofInstant(when, Cadence.ZONE), slot, title,
                blocks, new LinkedHashMap<>(Map.of(HIGHLIGHTS, itemIds)), status, e.scheduledAt(),
                status == NewsletterIssue.Status.SENT ? (lastSent != null ? lastSent : when) : null, sends, now);
    }

    /** The weekly newsletter's editions were {@code w-2026-W45}: their week's Monday at 9 is their slot. */
    static Instant slotOf(String id) {
        var m = java.util.regex.Pattern.compile("w-(\\d{4})-W(\\d{2})").matcher(id);
        if (!m.matches()) {
            return null;
        }
        var monday = LocalDate.of(Integer.parseInt(m.group(1)), 6, 1)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, Integer.parseInt(m.group(2)))
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atTime(9, 0).atZone(Cadence.ZONE).toInstant();
    }

    static String text(JsonNode doc, String field, String fallback) {
        var n = doc.get(field);
        return n == null || n.isNull() ? fallback : n.asText();
    }

    static JsonNode read(String json) {
        try {
            return Json.MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
