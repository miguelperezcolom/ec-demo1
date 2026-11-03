package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.Mailer;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterLinks;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterPreferences;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.navigation.Section;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sends the issues, and draws them for whoever reads them. Recipients are the enabled employees with an
 * email whom the newsletter site's audience admits and who did not unsubscribe from THAT newsletter; they
 * are grouped by profile hash, language and what they want of it; which blocks and items each group sees
 * is asked of the visibility function once per group, and one email goes per group — a group that sees
 * nothing gets nothing. The web version draws the same issue for one reader.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterService {

    final NewsletterIssueRepository issues;
    final SiteRepository sites;
    final NavigationRepository navigation;
    final EmployeeDirectory directory;
    final Visibility visibility;
    final NamedAudiences namedAudiences;
    final Mailer mailer;
    final Clock clock;
    final NewsletterPreferences preferences;
    final io.mateu.ecdemo1.contenthub.application.out.ItemRepository items;

    @Value("${content-hub.portal-url:http://localhost:8800/intranet}")
    String portalUrl = "http://localhost:8800/intranet";

    /** The tracked links (open pixel, item clicks); none outside Spring (tests) or when not wired. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    NewsletterLinks links = NewsletterLinks.NONE;

    /** What a REMOTE block becomes in an e-mail (the remote UIs' fallback); the renderer's own link without it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    io.mateu.ecdemo1.contenthub.application.out.RemoteBlockFallback remoteFallback;

    /**
     * A group of recipients: everyone with the same profile who reads it in the same language and wants the
     * same from it. {@code hash} names the group: the profile's hash, followed by the sections wanted when
     * they are not all ({@code abc123~editorial,noticias}).
     */
    public record Group(String hash, String language, EmployeeAttributes profile, List<Employee> recipients,
                        NewsletterPreferences.Preference preference) {
    }

    Site site(NewsletterIssue issue) {
        return sites.find(issue.newsletterId())
                .orElseThrow(() -> new IllegalStateException("No existe la newsletter " + issue.newsletterId()));
    }

    /** The issue's recipients, grouped. */
    public List<Group> groups(NewsletterIssue issue) {
        var site = site(issue);
        var policy = namedAudiences.policy();
        var byKey = new LinkedHashMap<String, Group>();
        for (var e : directory.all()) {
            if (!e.enabled() || e.email() == null || e.email().isBlank() || !policy.admits(site.audience(), e.attributes())) {
                continue;
            }
            var preference = preferences.of(e.userId(), site.id());
            if (!preference.subscribed()) {
                continue; // unsubscribed from this newsletter: nothing of it
            }
            var language = site.languageFor(e.attributes().language());
            var hash = preference.key().isEmpty() ? e.hash() : e.hash() + "~" + preference.key();
            var key = NewsletterIssue.groupKey(hash, language);
            byKey.computeIfAbsent(key, k -> new Group(hash, language, e.attributes().withLanguage(language),
                    new ArrayList<>(), preference)).recipients().add(e);
        }
        return List.copyOf(byKey.values());
    }

    /** Per section of the newsletter, the issue's items a profile sees and wants, in the issue's order. */
    public Map<String, List<VisibleItem>> itemsFor(NewsletterIssue issue, EmployeeAttributes profile,
                                                   NewsletterPreferences.Preference preference) {
        var out = new LinkedHashMap<String, List<VisibleItem>>();
        var ids = issue.itemIds();
        if (ids.isEmpty()) {
            return out;
        }
        var visible = visibility.visibles(profile, ContentQuery.ids(ids)).content().stream()
                .collect(Collectors.toMap(VisibleItem::itemId, i -> i, (a, b) -> a));
        issue.sections().forEach((key, list) -> {
            if (preference == null || preference.wants(key)) {
                out.put(key, list.stream().map(visible::get).filter(java.util.Objects::nonNull).toList());
            }
        });
        return out;
    }

    /** Whether a profile sees a block: the newsletter's audience AND the block's. */
    public boolean sees(Site site, Block block, EmployeeAttributes profile) {
        return namedAudiences.policy().admits(Audience.effective(site.audience(), block.audience()), profile);
    }

    /** What an intranet listing block of the issue shows a profile. */
    public List<VisibleItem> listing(Block b, EmployeeAttributes profile) {
        return switch (b.type()) {
            case FEATURED -> b.itemId() == null ? List.of() : visibility.item(profile, b.itemId()).map(List::of).orElse(List.of());
            case NOTICES -> visibility.visibles(profile, new ContentQuery(Set.of(ItemType.NOTICE), b.sectionId(), null, null,
                    0, limit(b))).content();
            case LATEST -> visibility.visibles(profile, new ContentQuery(Set.of(ItemType.ARTICLE, ItemType.PAGE),
                    b.sectionId(), null, null, 0, limit(b))).content();
            case AUTOMATIC_LISTING -> visibility.visibles(profile, new ContentQuery(Set.of(), b.sectionId(), null, null, 0,
                    limit(b))).content();
            default -> List.of();
        };
    }

    static int limit(Block b) {
        return b.limit() == null ? 5 : b.limit();
    }

    /** The newsletter's section names, in a language. */
    public Map<String, String> sectionNames(String siteId, String language) {
        return navigation.get(siteId).sections().stream().collect(Collectors.toMap(Section::id, s -> s.name(language),
                (a, b) -> a, LinkedHashMap::new));
    }

    /** Draws an issue for a profile — a group's email, or one reader's web version — with the given links. */
    public String render(NewsletterIssue issue, EmployeeAttributes profile, NewsletterPreferences.Preference preference,
                         NewsletterRenderer.Mode mode, String greeting, NewsletterRenderer.Links links) {
        var site = site(issue);
        var language = site.languageFor(profile.language());
        var p = profile.withLanguage(language);
        return NewsletterRenderer.render(new NewsletterRenderer.View(site, issue, mode, language,
                itemsFor(issue, p, preference), b -> listing(b, p), b -> sees(site, b, p),
                sectionNames(site.id(), language), greeting, links));
    }

    /** Whether a group gets anything of the issue: a block it sees that shows something. */
    boolean anythingFor(NewsletterIssue issue, Group group) {
        var site = site(issue);
        var items = itemsFor(issue, group.profile(), group.preference());
        return issue.blocks().stream().filter(b -> sees(site, b, group.profile())).anyMatch(b -> switch (b.type()) {
            case NEWSLETTER_SECTION -> !items.getOrDefault(io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter.sectionOf(b),
                    List.of()).isEmpty();
            case HTML, BANNER -> b.html() != null && !b.html().isBlank();
            case FEATURED, AUTOMATIC_LISTING, LATEST, NOTICES -> !listing(b, group.profile()).isEmpty();
            case MEDIA -> b.mediaId() != null;
            case QUICK_LINKS -> !b.links().isEmpty();
            case REMOTE -> b.remote() != null;
            default -> false; // greeting, pending, and any block type a newsletter does not draw
        });
    }

    /** The ids of the items a group gets, for the record. */
    List<String> itemIdsFor(NewsletterIssue issue, Group group) {
        return itemsFor(issue, group.profile(), group.preference()).values().stream().flatMap(List::stream)
                .map(VisibleItem::itemId).distinct().toList();
    }

    // ── links ───────────────────────────────────────────────────────────────────────────────────

    /** The portal's base: {@code https://host/intranet}. */
    public String portalUrl() {
        return portalUrl;
    }

    /** The newsletter site's page in the portal (its home: subscribe and unsubscribe there). */
    public String siteUrl(String siteId) {
        return portalUrl + "/n/" + siteId;
    }

    public String permalink(NewsletterIssue issue) {
        return siteUrl(issue.newsletterId()) + "/numeros/" + issue.id();
    }

    /** An item's page: the newsletter's own items under its site, the intranet's where they always are. */
    public static String itemPath(String base, VisibleItem item, String ownSite, java.util.function.Predicate<String> isOwn) {
        return isOwn.test(item.itemId()) ? base + "/n/" + ownSite + "/p/" + item.itemId() : base + "/noticias/" + item.itemId();
    }

    /** The email's links: absolute, through the tracker when it is a group's real send. */
    NewsletterRenderer.Links emailLinks(NewsletterIssue issue, String group, java.util.function.Predicate<String> isOwn) {
        var tracked = group == null ? NewsletterLinks.NONE : links;
        var permalink = permalink(issue);
        var site = issue.newsletterId();
        var logo = site(issue).definition().logoMediaId() == null ? null
                : portalUrl.replaceFirst("/intranet/?$", "") + "/_intranet/n/logo/" + site;
        return new NewsletterRenderer.Links() {
            @Override
            public String item(VisibleItem item) {
                return tracked.click(issue.id(), group, item.itemId(), itemPath(portalUrl, item, site, isOwn));
            }

            @Override
            public String rich(String html) {
                return NewsletterRenderer.emailRich(html, permalink);
            }

            @Override
            public String medium(String mediaId) {
                return null;
            }

            @Override
            public String permalink() {
                return permalink;
            }

            @Override
            public String unsubscribe() {
                return siteUrl(site);
            }

            @Override
            public String logo() {
                return logo;
            }

            @Override
            public String pixel() {
                return tracked.open(issue.id(), group);
            }

            @Override
            public String remote(Block block) {
                return remoteFallback == null ? null : remoteFallback.html(block, permalink);
            }
        };
    }

    /** Whether an item is one of a newsletter site's own (its page is under the site). */
    public boolean isOwnItem(String siteId, String itemId) {
        return items.find(itemId).map(i -> siteId.equals(i.siteId())).orElse(false);
    }

    /** Whether an item is one of the newsletter site's own (else the intranet's). */
    java.util.function.Predicate<String> ownItems(NewsletterIssue issue) {
        var ids = issue.itemIds();
        if (ids.isEmpty()) {
            return id -> false;
        }
        var own = new java.util.HashSet<String>();
        for (var id : ids) {
            items.find(id).filter(i -> issue.newsletterId().equals(i.siteId())).ifPresent(i -> own.add(id));
        }
        return own::contains;
    }

    // ── sending ─────────────────────────────────────────────────────────────────────────────────

    /** Sends every issue that is due; a failed group is retried on the next pass, the others are not resent. */
    public int sendDue() {
        var sent = 0;
        var now = clock.instant();
        for (var issue : issues.due(now).stream().filter(i -> i.due(now)).toList()) {
            try {
                sent += send(issue.id());
            } catch (RuntimeException e) {
                log.warn("Newsletter issue {} not sent: {}", issue.id(), e.getMessage());
                issues.find(issue.id()).ifPresent(i -> {
                    passFailed(i);
                    issues.save(i);
                });
            }
        }
        return sent;
    }

    private void passFailed(NewsletterIssue issue) {
        issue.sendFailed(clock.instant());
        if (issue.sendingStopped()) {
            log.error("Newsletter issue {}: sending stopped after repeated failures, until someone retries it", issue.id());
        }
    }

    @Transactional
    public int send(String issueId) {
        var issue = issues.find(issueId).orElseThrow();
        if (issue.status() != NewsletterIssue.Status.SCHEDULED) {
            return 0;
        }
        if (mailer.redirected()) {
            return sendDigest(issue);
        }
        var own = ownItems(issue);
        var emails = 0;
        var failed = false;
        var subject = issue.subject();
        for (var group : groups(issue)) {
            if (issue.groupSent(group.hash(), group.language())) {
                continue;
            }
            if (!anythingFor(issue, group)) {
                issue.recordGroup(new NewsletterIssue.GroupSend(group.hash(), group.language(), group.recipients().size(),
                        List.of(), clock.instant(), null));
                continue;
            }
            var key = NewsletterIssue.groupKey(group.hash(), group.language());
            try {
                mailer.send(group.recipients().stream().map(Employee::email).toList(), subject,
                        render(issue, group.profile(), group.preference(), NewsletterRenderer.Mode.EMAIL,
                                "es".equals(group.language()) ? "Hola," : "Hello,", emailLinks(issue, key, own)));
                issue.recordGroup(new NewsletterIssue.GroupSend(group.hash(), group.language(), group.recipients().size(),
                        itemIdsFor(issue, group), clock.instant(), null));
                emails++;
            } catch (RuntimeException e) {
                log.warn("Newsletter issue {} group {}/{} not sent: {}", issueId, group.hash(), group.language(), e.getMessage());
                issue.recordGroup(new NewsletterIssue.GroupSend(group.hash(), group.language(), group.recipients().size(),
                        List.of(), null, e.getMessage()));
                failed = true;
            }
        }
        if (failed) {
            passFailed(issue);
        } else {
            issue.markSent(clock.instant());
        }
        issues.save(issue);
        return emails;
    }

    /**
     * The demo's way: every message would land in the same mailbox, so the issue goes as ONE message with a
     * part per group — the version each group gets, and whom it was for — instead of one per group. Each
     * group is still recorded as sent (or failed) on its own, as the real sending does.
     */
    int sendDigest(NewsletterIssue issue) {
        var own = ownItems(issue);
        var pending = new ArrayList<Group>();
        for (var group : groups(issue)) {
            if (issue.groupSent(group.hash(), group.language())) {
                continue;
            }
            if (!anythingFor(issue, group)) {
                issue.recordGroup(new NewsletterIssue.GroupSend(group.hash(), group.language(), group.recipients().size(),
                        List.of(), clock.instant(), null));
                continue;
            }
            pending.add(group);
        }
        var emails = 0;
        var failed = false;
        if (!pending.isEmpty()) {
            var html = new StringBuilder("<div style=\"font-family:sans-serif;max-width:680px\">")
                    .append("<p style=\"background:#e8f0fe;padding:8px\">Demo: ").append(pending.size())
                    .append(" grupos de destinatarios en un solo correo. Fuera de la demo, cada grupo recibe solo")
                    .append(" su parte, en su idioma.</p>");
            var n = 0;
            for (var group : pending) {
                n++;
                html.append("<hr><p style=\"color:#555;font-size:13px\"><b>Grupo ").append(n).append(" de ")
                        .append(pending.size()).append("</b> · ").append(NewsletterRenderer.esc(group.language())).append(" · ")
                        .append(NewsletterRenderer.esc(describe(group.profile()))).append(" · ").append(group.recipients().size())
                        .append(" destinatario(s): ").append(NewsletterRenderer.esc(group.recipients().stream().map(Employee::name)
                                .collect(Collectors.joining(", ")))).append("</p>")
                        .append(render(issue, group.profile(), group.preference(), NewsletterRenderer.Mode.EMAIL,
                                "es".equals(group.language()) ? "Hola," : "Hello,",
                                emailLinks(issue, NewsletterIssue.groupKey(group.hash(), group.language()), own)));
            }
            html.append("</div>");
            var recipients = pending.stream().flatMap(g -> g.recipients().stream()).map(Employee::email).toList();
            String error = null;
            try {
                mailer.send(recipients, issue.subject() + " [" + pending.size() + " grupos]", html.toString());
                emails = 1;
            } catch (RuntimeException e) {
                log.warn("Newsletter issue {} not sent: {}", issue.id(), e.getMessage());
                error = e.getMessage();
                failed = true;
            }
            for (var group : pending) {
                issue.recordGroup(new NewsletterIssue.GroupSend(group.hash(), group.language(), group.recipients().size(),
                        error == null ? itemIdsFor(issue, group) : List.of(), error == null ? clock.instant() : null, error));
            }
        }
        if (failed) {
            passFailed(issue);
        } else {
            issue.markSent(clock.instant());
        }
        issues.save(issue);
        return emails;
    }

    /**
     * «Enviar prueba»: the issue as the editor would get it — their profile, all its sections — to their own
     * address (in the demo, to the redirect mailbox, like every message). Nothing is recorded.
     */
    public String sendTest(Reader reader, String issueId) {
        reader.require(EditorialPermission.EDIT);
        var issue = issues.find(issueId).orElseThrow();
        var me = directory.find(reader.userId()).orElse(null);
        var email = me == null ? null : me.email();
        if ((email == null || email.isBlank()) && !mailer.redirected()) {
            throw new IllegalStateException("No tienes email en el directorio: no hay adónde enviar la prueba");
        }
        var profile = reader.attributes();
        mailer.send(List.of(email == null || email.isBlank() ? reader.userId() + "@prueba" : email),
                "[Prueba] " + issue.subject(), render(issue, profile, NewsletterPreferences.Preference.DEFAULT,
                        NewsletterRenderer.Mode.EMAIL, "Hola, " + reader.name(), emailLinks(issue, null, ownItems(issue))));
        return email == null ? "el buzón de la demo" : email;
    }

    public static String esc(String s) {
        return NewsletterRenderer.esc(s);
    }

    /** A group's profile, for the digest: its attributes, in their canonical order. */
    static String describe(EmployeeAttributes profile) {
        return profile.values().isEmpty() ? "sin atributos" : new java.util.TreeMap<>(profile.values()).entrySet()
                .stream().map(e -> e.getKey().name().toLowerCase() + "=" + e.getValue())
                .collect(Collectors.joining(" · "));
    }

    // ── reading, in the portal ──────────────────────────────────────────────────────────────────

    /** The newsletters a reader is in the audience of (active ones), with what they want of each. */
    public List<Subscription> newslettersOf(Reader reader) {
        var policy = namedAudiences.policy();
        var mine = preferences.of(reader.userId());
        return sites.findAll().stream().filter(s -> s.kind() == Site.Kind.NEWSLETTER && s.active())
                .filter(s -> policy.admits(s.audience(), reader.attributes()))
                .map(s -> new Subscription(s, mine.getOrDefault(s.id(), NewsletterPreferences.Preference.DEFAULT),
                        latestSent(s.id()).orElse(null)))
                .toList();
    }

    public record Subscription(Site site, NewsletterPreferences.Preference preference, NewsletterIssue latest) {
    }

    /** A newsletter site the reader may read: active and for them. */
    public Optional<Site> readable(Reader reader, String siteId) {
        return sites.find(siteId).filter(s -> s.kind() == Site.Kind.NEWSLETTER && s.active())
                .filter(s -> namedAudiences.policy().admits(s.audience(), reader.attributes()));
    }

    /** The sent issues of a newsletter, the latest first: its archive. */
    public List<NewsletterIssue> archive(String siteId) {
        return issues.ofNewsletter(siteId).stream().filter(i -> i.status() == NewsletterIssue.Status.SENT)
                .sorted(Comparator.comparing(NewsletterIssue::number).reversed()).toList();
    }

    public Optional<NewsletterIssue> latestSent(String siteId) {
        return archive(siteId).stream().findFirst();
    }

    /** A sent issue of a newsletter the reader may read. */
    public Optional<NewsletterIssue> readableIssue(Reader reader, String siteId, String issueId) {
        return readable(reader, siteId).flatMap(s -> issues.find(issueId))
                .filter(i -> i.newsletterId().equals(siteId) && i.status() == NewsletterIssue.Status.SENT);
    }

    /**
     * One block of an issue as one reader gets it, as data rather than markup (the MCP tools): its text, the
     * items it lists (only those the reader sees), its links. {@code html} is the block's rich text, as written.
     */
    public record ReadBlock(BlockType type, String title, String html, List<VisibleItem> items, List<Block.Link> links) {
    }

    /**
     * A sent issue as the reader gets it — what its web version draws for them: the blocks they see, in their
     * language, with the items they see and, when subscribed, only the sections they want. A block with
     * nothing for them is left out, as in the email.
     */
    public List<ReadBlock> read(Reader reader, NewsletterIssue issue) {
        var site = site(issue);
        var language = site.languageFor(reader.attributes().language());
        var profile = reader.attributes().withLanguage(language);
        var preference = preference(reader, site.id());
        var sectionItems = itemsFor(issue, profile, preference.subscribed() ? preference : null);
        var names = sectionNames(site.id(), language);
        var out = new ArrayList<ReadBlock>();
        for (var stored : issue.blocks()) {
            if (!sees(site, stored, profile)) {
                continue;
            }
            var b = stored.in(language, site.defaultLanguage());
            var title = b.title() == null ? "" : b.title();
            var block = switch (b.type()) {
                case HTML, BANNER -> b.html() == null || b.html().isBlank() ? null
                        : new ReadBlock(b.type(), title, b.html(), List.of(), List.of());
                case MEDIA -> b.mediaId() == null ? null : new ReadBlock(b.type(), title, "", List.of(), List.of());
                case QUICK_LINKS -> b.links().isEmpty() ? null : new ReadBlock(b.type(), title, "", List.of(), b.links());
                case FEATURED, AUTOMATIC_LISTING, LATEST, NOTICES -> {
                    var list = listing(b, profile);
                    yield list.isEmpty() ? null : new ReadBlock(b.type(), title, "", list, List.of());
                }
                case NEWSLETTER_SECTION -> {
                    var key = io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter.sectionOf(b);
                    var list = sectionItems.getOrDefault(key, List.of());
                    yield list.isEmpty() ? null : new ReadBlock(b.type(), title.isBlank() ? names.getOrDefault(key, key) : title,
                            "", list, List.of());
                }
                case REMOTE -> b.remote() == null ? null : new ReadBlock(b.type(), title, "", List.of(), List.of());
                default -> null; // the greeting, pending confirmations: nothing of the issue's own
            };
            if (block != null) {
                out.add(block);
            }
        }
        return out;
    }

    public NewsletterPreferences.Preference preference(Reader reader, String siteId) {
        return preferences.of(reader.userId(), siteId);
    }

    /** Subscribes or unsubscribes, and chooses sections (only ever narrows). Not while viewing as another profile. */
    public void subscribe(Reader reader, String siteId, boolean subscribed, Set<String> sections) {
        if (reader.viewingAs() != null) {
            throw new IllegalStateException("Estás viendo la intranet como otro perfil: tus suscripciones no se tocan");
        }
        readable(reader, siteId).orElseThrow(() -> new IllegalArgumentException("Esa newsletter no es para ti"));
        var known = navigation.get(siteId).sections().stream().map(Section::id).collect(Collectors.toSet());
        var chosen = new java.util.HashSet<>(sections == null ? Set.of() : sections);
        chosen.retainAll(known);
        preferences.save(reader.userId(), siteId, new NewsletterPreferences.Preference(subscribed, chosen));
    }
}
