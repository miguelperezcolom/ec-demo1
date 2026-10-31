package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.Mailer;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterEdition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Composes and sends newsletter editions. Recipients are grouped by profile hash and language; which of
 * the edition's items each group sees is asked of the visibility function once per group; one email
 * goes per group, and a group that sees none of the items gets nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterService {

    final NewsletterRepository editions;
    final EmployeeDirectory directory;
    final Visibility visibility;
    final NamedAudiences namedAudiences;
    final Mailer mailer;
    final Clock clock;

    @Value("${content-hub.portal-url:http://localhost:8800/intranet}")
    String portalUrl;

    /** A group of recipients: everyone with the same profile who reads the same language. */
    public record Group(String hash, String language, EmployeeAttributes profile, List<Employee> recipients) {
    }

    @Transactional
    public String compose(Reader reader, String title, String intro, List<String> itemIds, Audience audience) {
        reader.require(EditorialPermission.ADMIN);
        var id = "n" + UUID.randomUUID().toString().substring(0, 8);
        editions.save(NewsletterEdition.compose(id, title, intro, itemIds, audience));
        return id;
    }

    @Transactional
    public void edit(Reader reader, String id, String title, String intro, List<String> itemIds, Audience audience) {
        reader.require(EditorialPermission.ADMIN);
        var e = editions.find(id).orElseThrow();
        e.edit(title, intro, itemIds, audience);
        editions.save(e);
    }

    @Transactional
    public void schedule(Reader reader, String id, Instant at) {
        reader.require(EditorialPermission.ADMIN);
        var e = editions.find(id).orElseThrow();
        e.schedule(at == null ? clock.instant() : at);
        editions.save(e);
    }

    /** The edition's recipients, grouped: enabled employees with an email whom the edition's audience admits. */
    public List<Group> groups(NewsletterEdition edition) {
        var policy = namedAudiences.policy();
        var byKey = new LinkedHashMap<String, Group>();
        for (var e : directory.all()) {
            if (!e.enabled() || e.email() == null || e.email().isBlank()
                    || !policy.admits(edition.audience(), e.attributes())) {
                continue;
            }
            var key = NewsletterEdition.groupKey(e.hash(), e.attributes().language());
            byKey.computeIfAbsent(key, k -> new Group(e.hash(), e.attributes().language(), e.attributes(),
                    new ArrayList<>())).recipients().add(e);
        }
        return List.copyOf(byKey.values());
    }

    /** The items of the edition a profile sees, in the edition's order and in its language. */
    public List<VisibleItem> itemsFor(NewsletterEdition edition, EmployeeAttributes profile) {
        if (edition.itemIds().isEmpty()) {
            return List.of();
        }
        var visible = visibility.visibles(profile, ContentQuery.ids(edition.itemIds())).content();
        return visible.stream().sorted(Comparator.comparingInt(i -> edition.itemIds().indexOf(i.itemId()))).toList();
    }

    /** Sends every edition that is due; a failed group is retried on the next pass, the others are not resent. */
    public int sendDue() {
        var sent = 0;
        for (var edition : editions.due(clock.instant())) {
            sent += send(edition.id());
        }
        return sent;
    }

    @Transactional
    public int send(String editionId) {
        var edition = editions.find(editionId).orElseThrow();
        var emails = 0;
        var failed = false;
        for (var group : groups(edition)) {
            if (edition.groupSent(group.hash(), group.language())) {
                continue;
            }
            var items = itemsFor(edition, group.profile());
            if (items.isEmpty()) {
                edition.recordGroup(new NewsletterEdition.GroupSend(group.hash(), group.language(),
                        group.recipients().size(), List.of(), clock.instant(), null));
                continue;
            }
            try {
                mailer.send(group.recipients().stream().map(Employee::email).toList(),
                        edition.title() + " [" + group.language() + " · " + group.hash() + "]",
                        render(edition, items, group.language()));
                edition.recordGroup(new NewsletterEdition.GroupSend(group.hash(), group.language(),
                        group.recipients().size(), items.stream().map(VisibleItem::itemId).toList(), clock.instant(),
                        null));
                emails++;
            } catch (RuntimeException e) {
                log.warn("Newsletter {} group {}/{} not sent: {}", editionId, group.hash(), group.language(),
                        e.getMessage());
                edition.recordGroup(new NewsletterEdition.GroupSend(group.hash(), group.language(),
                        group.recipients().size(), List.of(), null, e.getMessage()));
                failed = true;
            }
        }
        if (!failed) {
            edition.markSent();
        }
        editions.save(edition);
        return emails;
    }

    public String render(NewsletterEdition edition, List<VisibleItem> items, String language) {
        var html = new StringBuilder("<div style=\"font-family:sans-serif;max-width:640px\">")
                .append("<h1>").append(esc(edition.title())).append("</h1>");
        if (!edition.intro().isBlank()) {
            html.append("<p>").append(esc(edition.intro())).append("</p>");
        }
        for (var item : items) {
            html.append("<h2><a href=\"").append(esc(portalUrl)).append("/noticias/").append(esc(item.itemId()))
                    .append("\">").append(esc(item.title())).append("</a></h2>")
                    .append("<p>").append(esc(item.summary())).append("</p>");
        }
        html.append("<p style=\"color:#888;font-size:12px\">").append("es".equals(language)
                ? "Recibes este correo como empleado. Solo incluye lo que puedes ver en la intranet."
                : "You receive this email as an employee. It only includes what you can see on the intranet.")
                .append("</p></div>");
        return html.toString();
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public Map<String, Object> stats() {
        return Map.of("editions", editions.findAll().size());
    }
}
