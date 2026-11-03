package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.HomePages;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.navigation.Navigation;
import io.mateu.ecdemo1.contenthub.domain.navigation.Section;
import io.mateu.ecdemo1.contenthub.domain.newsletter.AutomaticContent;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The editorial side of the newsletters: creating one (a site of kind NEWSLETTER, its sections, its
 * newsletter settings), configuring it, and the life of its issues — a new one pre-filled (the layout's
 * blocks, and what was published since the previous issue in each section), edits, schedule, back to
 * draft, cancel — and the scheduler's pass that opens the draft of every issue that is due.
 *
 * <p>Permissions as everywhere: {@code content:edit} composes and edits; scheduling — sending — is
 * publishing ({@code content:publish}), and so is a newsletter that sends its issues without review.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterEditorial {

    static final Set<ItemType> INTRANET_TYPES = Set.of(ItemType.ARTICLE, ItemType.NOTICE, ItemType.PAGE);
    static final Set<ItemType> OWN_TYPES = Set.of(ItemType.ARTICLE, ItemType.NOTICE);

    final SiteRepository sites;
    final NewsletterRepository newsletters;
    final NewsletterIssueRepository issues;
    final NavigationRepository navigation;
    final ItemRepository items;
    final HomePages homes;
    final EmployeeDirectory directory;
    final NamedAudiences namedAudiences;
    final Visibility visibility;
    final ItemCommands itemCommands;
    final Clock clock;
    /** Its menu (SiteMenus, owned by the site); none in tests that do not need it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    io.mateu.ecdemo1.contenthub.application.menu.NewsletterMenus menus;

    /** With its menus (outside Spring: the tests). */
    public NewsletterEditorial withMenus(io.mateu.ecdemo1.contenthub.application.menu.NewsletterMenus menus) {
        this.menus = menus;
        return this;
    }

    /** A section of the newsletter as an editor sets it: its id, its name, and the intranet sections it takes. */
    public record SectionSpec(String id, String name, List<String> sources) {
    }

    // ── newsletters ─────────────────────────────────────────────────────────────────────────────

    /** A new newsletter: its site, its sections, and its settings (a layout made of them, if none given). */
    @Transactional
    public String create(Reader reader, Site.Definition definition, List<SectionSpec> sections, Newsletter.Settings settings) {
        reader.require(EditorialPermission.EDIT);
        if (settings.autoSend()) {
            reader.require(EditorialPermission.PUBLISH);
        }
        var id = Site.newId(definition.name());
        createWithId(id, definition, sections, settings);
        return id;
    }

    /** As {@link #create}, with the id given and nobody asking (the migration, the seed). */
    @Transactional
    public void createWithId(String id, Site.Definition definition, List<SectionSpec> sections, Newsletter.Settings settings) {
        if (sites.find(id).isPresent()) {
            throw new IllegalStateException("Ya existe el sitio " + id);
        }
        sites.save(Site.create(id, Site.Kind.NEWSLETTER, definition));
        var nav = navigation.get(id);
        apply(nav, sections, definition.languages());
        navigation.save(id, nav);
        newsletters.save(Newsletter.create(id, complete(settings, nav, sections), clock.instant()));
        if (menus != null) {
            menus.seedIfAbsent(id);
        }
    }

    /** Changes a newsletter: its site (identity, audience…), its sections and its settings. */
    @Transactional
    public void configure(Reader reader, String id, Site.Definition definition, List<SectionSpec> sections,
                          Newsletter.Settings settings) {
        reader.require(EditorialPermission.EDIT);
        var newsletter = newsletter(id);
        if (settings.autoSend() && !newsletter.settings().autoSend()) {
            reader.require(EditorialPermission.PUBLISH);
        }
        var site = sites.find(id).orElseThrow();
        var wasActive = site.active();
        site.configure(definition);
        sites.save(site);
        var nav = navigation.get(id);
        apply(nav, sections, definition.languages());
        navigation.save(id, nav);
        newsletter.configure(complete(settings, nav, sections), clock.instant());
        if (definition.active() && !wasActive) {
            newsletter.restart(clock.instant());
        }
        newsletters.save(newsletter);
    }

    /** The settings with the sections' sources, and a layout made of the sections when it has none; checked. */
    static Newsletter.Settings complete(Newsletter.Settings settings, Navigation nav, List<SectionSpec> sections) {
        var sources = new LinkedHashMap<String, List<String>>();
        for (var s : sections == null ? List.<SectionSpec>of() : sections) {
            sources.put(s.id(), s.sources() == null ? List.of() : s.sources());
        }
        var layout = settings.layout().isEmpty() ? Newsletter.Settings.defaultLayout(nav.sections()) : settings.layout();
        var known = nav.sections().stream().map(Section::id).collect(java.util.stream.Collectors.toSet());
        for (var b : layout) {
            if (b.type() == io.mateu.ecdemo1.contenthub.domain.item.BlockType.NEWSLETTER_SECTION
                    && !known.contains(Newsletter.sectionOf(b))) {
                throw new IllegalArgumentException("La maqueta usa la sección «" + Newsletter.sectionOf(b)
                        + "», que la newsletter no tiene");
            }
        }
        return new Newsletter.Settings(layout, sources, settings.cadence(), settings.autoSend());
    }

    /** The site's tree as the sections say: top-level sections, added, renamed or removed (a section with items stays). */
    void apply(Navigation nav, List<SectionSpec> sections, List<String> languages) {
        if (sections == null) {
            return;
        }
        var wanted = new HashSet<String>();
        for (var s : sections) {
            if (s.id() == null || !s.id().matches("[a-z0-9][a-z0-9-]{0,40}")) {
                throw new IllegalArgumentException("El id de una sección son minúsculas, cifras y guiones: «" + s.id() + "»");
            }
            wanted.add(s.id());
            var names = new LinkedHashMap<String, String>();
            names.put(languages.isEmpty() ? "es" : languages.getFirst(), s.name() == null || s.name().isBlank() ? s.id() : s.name());
            if (nav.find(s.id()).isPresent()) {
                nav.rename(s.id(), names);
            } else {
                nav.add(s.id(), null, names);
            }
        }
        for (var existing : nav.sections()) {
            if (existing.parentId() == null && !wanted.contains(existing.id()) && nav.children(existing.id()).isEmpty()) {
                nav.remove(existing.id());
            }
        }
    }

    public Newsletter newsletter(String id) {
        return newsletters.find(id).orElseThrow(() -> new IllegalArgumentException("No existe la newsletter " + id));
    }

    /** A new page of the newsletter's own («Quiénes somos», «Contacto»): a draft page of its site, to fill in. */
    @Transactional
    public String newPage(Reader reader, String siteId, String title) {
        reader.require(EditorialPermission.EDIT);
        newsletter(siteId);
        var site = sites.find(siteId).orElseThrow();
        var language = site.definition().languages().isEmpty() ? "es" : site.definition().languages().getFirst();
        var name = title == null || title.isBlank() ? "Nueva página" : title;
        var id = itemCommands.createInSite(reader, siteId, ItemType.PAGE, language, new ItemContent(null, null,
                Map.of(language, new Translation(name, "", "")), List.of(), null, null, false, false, null, null));
        if (menus != null) {
            menus.addPage(siteId, id, name);
        }
        return id;
    }

    /** The newsletter's own pages, published or not — its menu's (the portal shows the published ones a reader sees). */
    public List<Item> pages(String siteId) {
        return items.findAll().stream().filter(i -> siteId.equals(i.siteId()) && i.type() == ItemType.PAGE).toList();
    }

    // ── issues ──────────────────────────────────────────────────────────────────────────────────

    /** A new issue now, pre-filled: the layout's blocks and what was published since the previous issue. */
    @Transactional
    public String newIssue(Reader reader, String newsletterId) {
        reader.require(EditorialPermission.EDIT);
        return open(newsletter(newsletterId), null, clock.instant()).id();
    }

    NewsletterIssue open(Newsletter newsletter, Instant slot, Instant now) {
        var site = sites.find(newsletter.id()).orElseThrow();
        var previous = issues.latest(newsletter.id()).orElse(null);
        var picked = automatic(newsletter, site, previous, now);
        var issue = newsletter.openIssue(site.name(), slot, picked, now);
        newsletters.save(newsletter);
        issues.save(issue);
        return issue;
    }

    /**
     * The scheduler's pass: for every active newsletter whose cadence has a slot due, the draft of its next
     * issue — scheduled right away if the newsletter sends without review and it has something. A slot opens
     * one issue, ever: the newsletter remembers it and the store refuses a second issue for it.
     */
    @Transactional
    public List<String> createDue(Instant now) {
        var out = new ArrayList<String>();
        for (var newsletter : newsletters.findAll()) {
            var site = sites.find(newsletter.id()).orElse(null);
            if (site == null || !site.active()) {
                continue;
            }
            var slot = newsletter.dueSlot(now).orElse(null);
            if (slot == null) {
                continue;
            }
            var issue = open(newsletter, slot, now);
            if (newsletter.settings().autoSend()) {
                try {
                    issue.schedule(now);
                    issues.save(issue);
                } catch (IllegalStateException e) {
                    log.info("Newsletter {} nº {}: nothing to send, left as a draft", newsletter.id(), issue.number());
                }
            }
            log.info("Newsletter {}: nº {} opened for {}{}", newsletter.id(), issue.number(), slot,
                    issue.status() == NewsletterIssue.Status.SCHEDULED ? ", scheduled" : " (draft)");
            out.add(issue.id());
        }
        return out;
    }

    /**
     * What an issue gets by itself, per section: the items published since the previous issue — the site's
     * own articles and notices in each section, and the intranet's (news, notices, pages, not the homes) in
     * the sections mapped to it — that someone of the newsletter's audience sees.
     */
    public Map<String, List<String>> automatic(Newsletter newsletter, Site site, NewsletterIssue previous, Instant now) {
        var since = AutomaticContent.since(previous, newsletter.settings().cadence(), now);
        var siteNav = navigation.get(site.id());
        var siteSections = siteNav.sections().stream().map(Section::id).toList();
        var intranetNav = navigation.get();
        var excluded = new HashSet<>(homes.pageIds());
        excluded.addAll(homes.pageIds(site.id()));
        var candidates = new ArrayList<AutomaticContent.Candidate>();
        for (var i : items.findAll()) {
            if (i.status() != ItemStatus.PUBLISHED || excluded.contains(i.id())) {
                continue;
            }
            var own = site.id().equals(i.siteId());
            if (!(own ? OWN_TYPES : INTRANET_TYPES).contains(i.type()) || !own && !Site.INTRANET.equals(i.siteId())) {
                continue;
            }
            i.publishedVersion().ifPresent(v -> candidates.add(new AutomaticContent.Candidate(i.id(), i.siteId(),
                    v.content().sectionId(), v.content().publishAt() != null ? v.content().publishAt() : v.publishedAt(),
                    v.content().expireAt())));
        }
        var seen = seenByTheAudience(site, candidates.stream().map(AutomaticContent.Candidate::itemId).toList(), now);
        return AutomaticContent.pick(newsletter, siteSections, candidates, since, now, intranetNav::ancestors, seen::contains);
    }

    /** The items someone of the site's audience sees: asked once per profile of the audience. */
    Set<String> seenByTheAudience(Site site, List<String> ids, Instant now) {
        var out = new HashSet<String>();
        if (ids.isEmpty()) {
            return out;
        }
        var policy = namedAudiences.policy();
        var profiles = new LinkedHashMap<String, io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes>();
        for (var e : directory.all()) {
            if (e.enabled() && policy.admits(site.audience(), e.attributes())) {
                profiles.putIfAbsent(e.hash(), e.attributes());
            }
        }
        for (var profile : profiles.values()) {
            visibility.visibles(profile, now, ContentQuery.ids(ids)).content().stream().map(VisibleItem::itemId)
                    .forEach(out::add);
        }
        return out;
    }

    @Transactional
    public void editIssue(Reader reader, String id, String subject, LocalDate date, List<Block> blocks,
                          Map<String, List<String>> sections) {
        reader.require(EditorialPermission.EDIT);
        var issue = issue(id);
        issue.edit(subject, date, blocks, sections, clock.instant());
        issues.save(issue);
    }

    /** The visual editor's save: the blocks only, unless someone changed the issue since it was opened. */
    @Transactional
    public void editIssueBlocks(Reader reader, String id, List<Block> blocks, String updatedAt) {
        reader.require(EditorialPermission.EDIT);
        var issue = issue(id);
        if (updatedAt != null && issue.updatedAt() != null && !updatedAt.equals(issue.updatedAt().toString())) {
            throw new IllegalStateException("Alguien ha cambiado este número desde que lo abriste: recarga");
        }
        issue.editBlocks(blocks, clock.instant());
        issues.save(issue);
    }

    /** «Añadir lo nuevo»: what was published since the issue was opened, after what it has. */
    @Transactional
    public int addNew(Reader reader, String id) {
        reader.require(EditorialPermission.EDIT);
        var issue = issue(id);
        var newsletter = newsletter(issue.newsletterId());
        var site = sites.find(issue.newsletterId()).orElseThrow();
        var previous = issues.previous(issue.newsletterId(), issue.number()).orElse(null);
        var added = issue.addItems(automatic(newsletter, site, previous, clock.instant()), clock.instant());
        issues.save(issue);
        return added;
    }

    /** Sending is publishing: it needs the same permission. Empty {@code at}: now. */
    @Transactional
    public void schedule(Reader reader, String id, Instant at) {
        reader.require(EditorialPermission.PUBLISH);
        var issue = issue(id);
        issue.schedule(at == null ? clock.instant() : at);
        issues.save(issue);
    }

    /** After it stopped trying: the groups still unsent go on the next pass. */
    @Transactional
    public void retrySending(Reader reader, String id) {
        reader.require(EditorialPermission.PUBLISH);
        var issue = issue(id);
        issue.retrySending();
        issues.save(issue);
    }

    @Transactional
    public void unschedule(Reader reader, String id) {
        reader.require(EditorialPermission.PUBLISH);
        var issue = issue(id);
        issue.unschedule();
        issues.save(issue);
    }

    @Transactional
    public void cancel(Reader reader, String id) {
        reader.require(EditorialPermission.PUBLISH);
        var issue = issue(id);
        issue.cancel();
        issues.save(issue);
    }

    public NewsletterIssue issue(String id) {
        return issues.find(id).orElseThrow(() -> new IllegalArgumentException("No existe el número " + id));
    }

    public Optional<NewsletterIssue> findIssue(String id) {
        return issues.find(id);
    }
}
