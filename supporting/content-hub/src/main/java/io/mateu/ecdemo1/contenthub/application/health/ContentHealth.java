package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.ecdemo1.contenthub.application.out.AltTextGenerator;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.HealthRepository;
import io.mateu.ecdemo1.contenthub.application.out.HealthRepository.Snooze;
import io.mateu.ecdemo1.contenthub.application.out.HealthRepository.StoredFinding;
import io.mateu.ecdemo1.contenthub.application.out.HomePages;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaDescriptions;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.out.MenuRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.application.out.SurveyRepository;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.out.UsageRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.health.Finding;
import io.mateu.ecdemo1.contenthub.domain.health.HealthCheck;
import io.mateu.ecdemo1.contenthub.domain.media.MediaKind;
import io.mateu.ecdemo1.contenthub.domain.navigation.Section;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * «Salud del contenido»: runs the checks (on demand and every night), keeps their findings, and does what
 * the screen offers on one — mark it as reviewed for a while, delete an orphan medium (content:admin, and
 * only if it is still an orphan), suggest a text alternative with the AI and apply it once the editor has
 * read it. Nothing is ever fixed or deleted by itself.
 */
public class ContentHealth {

    private static final Logger log = LoggerFactory.getLogger(ContentHealth.class);

    /** A text alternative is short: a screen reader reads it whole. */
    public static final int MAX_ALT = 250;
    /** What the model is sent at most (its limit per image). */
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    /** The sources the checks read. */
    public record Sources(ItemRepository items, MediaRepository media, MediaDescriptions descriptions,
                          SlotRepository slots, HomePages homes, NavigationRepository navigation,
                          MenuRepository menus, UiSources uiSources, EmployeeDirectory directory,
                          SurveyRepository surveys, NewsletterRepository newsletters,
                          io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository issues,
                          io.mateu.ecdemo1.contenthub.application.out.SiteRepository sites, UsageRepository usage) {
    }

    /** A finding as the screen shows it: with its «revisado» mark while it lasts. */
    public record View(StoredFinding stored, Snooze snooze) {
        public Finding finding() {
            return stored.finding();
        }

        public boolean reviewed() {
            return snooze != null;
        }
    }

    /** The counts per check (open ones, and those marked as reviewed), the last run, and whether one is going. */
    public record Summary(Map<HealthCheck, Integer> open, Map<HealthCheck, Integer> reviewed,
                          Optional<HealthRepository.Run> lastRun, boolean running) {
    }

    final Sources sources;
    final HealthRepository store;
    final LinkChecker links;
    final AltTextGenerator ai;
    final HealthSettings settings;
    final Set<String> internalHosts;
    final Clock clock;
    final Executor background;
    final AtomicBoolean running = new AtomicBoolean();

    public ContentHealth(Sources sources, HealthRepository store, LinkChecker links, AltTextGenerator ai,
                         HealthSettings settings, Set<String> internalHosts, Clock clock, Executor background) {
        this.sources = sources;
        this.store = store;
        this.links = links;
        this.ai = ai;
        this.settings = settings;
        this.internalHosts = Set.copyOf(internalHosts);
        this.clock = clock;
        this.background = background;
    }

    // ---- running ------------------------------------------------------------------------------------------

    /**
     * The night's run, once per day: a second call for the same day — another tick, a restart, another
     * replica — finds it claimed and does nothing.
     *
     * @return whether this call ran it
     */
    public boolean nightly(LocalDate day) {
        var runId = newRunId();
        if (!store.claimNightly(day, runId, clock.instant())) {
            log.info("Content health: the night of {} is already done or under way", day);
            return false;
        }
        execute(runId);
        return true;
    }

    /** «Comprobar ahora»: in the background (external links take a while). False if one is already going. */
    public boolean startNow(Reader reader) {
        reader.require(EditorialPermission.EDIT);
        if (running.get()) {
            return false;
        }
        var runId = newRunId();
        store.startRun(runId, "ON_DEMAND", reader.userId(), clock.instant());
        background.execute(() -> execute(runId));
        return true;
    }

    /** A run now, on this thread (tests, and the scheduler through {@link #nightly}). */
    public HealthRepository.Run runNow(String by) {
        var runId = newRunId();
        store.startRun(runId, "ON_DEMAND", by, clock.instant());
        execute(runId);
        return store.lastRun().orElseThrow();
    }

    void execute(String runId) {
        if (!running.compareAndSet(false, true)) {
            store.finishRun(runId, Map.of(), "ya había otra comprobación en marcha", clock.instant());
            return;
        }
        try {
            var snapshot = snapshot();
            var external = links.check(HealthChecks.externalUrls(snapshot));
            var findings = HealthChecks.all(snapshot, external, settings);
            store.replaceFindings(findings, clock.instant());
            var counts = new LinkedHashMap<String, Integer>();
            for (var check : HealthCheck.values()) {
                counts.put(check.name(), (int) findings.stream().filter(f -> f.check() == check).count());
            }
            store.finishRun(runId, counts, null, clock.instant());
            log.info("Content health: {} findings {}", findings.size(), counts);
        } catch (RuntimeException e) {
            log.warn("Content health run failed: {}", e.toString(), e);
            store.finishRun(runId, Map.of(), String.valueOf(e.getMessage()), clock.instant());
        } finally {
            running.set(false);
        }
    }

    /** Everything the checks look at, read now. */
    public ContentSnapshot snapshot() {
        var now = clock.instant();
        var s = sources;
        var media = new ArrayList<io.mateu.ecdemo1.contenthub.domain.media.MediaAsset>();
        for (int offset = 0; ; offset += 500) {
            var page = s.media().search("", offset, 500);
            media.addAll(page);
            if (page.size() < 500) {
                break;
            }
        }
        var menus = s.menus().siteIds().stream().map(id -> s.menus().find(id)).flatMap(Optional::stream).toList();
        var analyticsSince = analyticsSince();
        var window = HealthChecks.unviewedWindowStart(now, analyticsSince, settings);
        Set<String> viewed = window.map(start -> s.usage().top(UsageRepository.Metric.VIEWS_BY_ITEM,
                        LocalDate.ofInstant(start, HealthChecks.ZONE), 1_000_000).stream()
                .filter(r -> r.value() > 0).map(UsageRepository.Ranked::key).collect(Collectors.toSet()))
                .orElse(Set.of());
        // every site: the intranet always (it may have no row yet), and each one stored
        var sites = new java.util.LinkedHashMap<String, String>();
        sites.put(io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET, "Intranet");
        s.sites().findAll().forEach(site -> sites.put(site.id(), site.name()));
        var homes = new java.util.LinkedHashMap<String, List<String>>();
        var sections = new java.util.LinkedHashMap<String, Set<String>>();
        for (var site : sites.keySet()) {
            homes.put(site, s.homes().pageIds(site));
            sections.put(site, s.navigation().get(site).sections().stream().map(Section::id).collect(Collectors.toSet()));
        }
        var layouts = new java.util.LinkedHashMap<String, List<io.mateu.ecdemo1.contenthub.domain.item.Block>>();
        s.newsletters().findAll().forEach(n -> layouts.put(n.id(), n.settings() == null || n.settings().layout() == null
                ? List.of() : n.settings().layout()));
        var uiSources = new java.util.LinkedHashMap<String, Boolean>();
        s.uiSources().all().forEach(u -> uiSources.put(u.id(), u.active()));
        return new ContentSnapshot(now, s.items().findAll(), media, s.descriptions().all(), s.slots().findAll(),
                sites, homes, sections, menus, s.issues().findAll(), layouts, uiSources,
                s.directory().all().stream().map(Employee::userId).collect(Collectors.toSet()),
                s.surveys().findAll().stream().map(x -> x.id()).collect(Collectors.toSet()),
                internalHosts, viewed, analyticsSince);
    }

    /** Since when usage is recorded: the first raw event, or the first day of the kept daily figures. */
    Instant analyticsSince() {
        var usage = sources.usage();
        var first = usage.firstEvent().orElse(null);
        var days = usage.perDay(UsageRepository.Metric.VIEWS, LocalDate.of(2000, 1, 1)).keySet().stream()
                .min(Comparator.naturalOrder()).map(d -> d.atStartOfDay(HealthChecks.ZONE).toInstant()).orElse(null);
        return Stream.of(first, days).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
    }

    static String newRunId() {
        return "h" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ---- reading ------------------------------------------------------------------------------------------

    public List<View> views() {
        var now = clock.instant();
        var snoozes = store.snoozes();
        return store.findings().stream().map(f -> {
            var snooze = snoozes.get(f.finding().key());
            return new View(f, snooze != null && snooze.until().isAfter(now) ? snooze : null);
        }).toList();
    }

    /** Each site's name, by id: the intranet's and each newsletter's. */
    public Map<String, String> siteNames() {
        var out = new LinkedHashMap<String, String>();
        out.put(io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET, "Intranet");
        sources.sites().findAll().forEach(s -> out.put(s.id(), s.name()));
        return out;
    }

    public Optional<View> view(String key) {
        return views().stream().filter(v -> v.finding().key().equals(key)).findFirst();
    }

    public Summary summary() {
        var open = new EnumMap<HealthCheck, Integer>(HealthCheck.class);
        var reviewed = new EnumMap<HealthCheck, Integer>(HealthCheck.class);
        for (var check : HealthCheck.values()) {
            open.put(check, 0);
            reviewed.put(check, 0);
        }
        for (var v : views()) {
            (v.reviewed() ? reviewed : open).merge(v.finding().check(), 1, Integer::sum);
        }
        return new Summary(open, reviewed, store.lastRun(), running.get());
    }

    // ---- acting on a finding ------------------------------------------------------------------------------

    /** «Marcar como revisado»: out of the counts for {@code days} days; it comes back if still there then. */
    public void snooze(Reader reader, String key, int days, String note) {
        reader.require(EditorialPermission.EDIT);
        if (store.finding(key).isEmpty()) {
            throw new IllegalArgumentException("Ese aviso ya no está");
        }
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("Entre 1 y 365 días");
        }
        var now = clock.instant();
        store.snooze(new Snooze(key, now.plus(Duration.ofDays(days)), reader.userId(), note, now));
    }

    public void unsnooze(Reader reader, String key) {
        reader.require(EditorialPermission.EDIT);
        store.unsnooze(key);
    }

    /**
     * Deletes an orphan medium — content:admin only, confirmed on the screen, and only if nothing uses it
     * now (something may have started to since the run).
     */
    public void deleteOrphan(Reader reader, String mediaId) {
        reader.require(EditorialPermission.ADMIN);
        var asset = sources.media().find(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Ese medio ya no existe"));
        if (!HealthChecks.orphan(snapshot(), mediaId)) {
            throw new IllegalStateException("«" + asset.title() + "» ya se usa en algún contenido: no se borra");
        }
        sources.media().delete(mediaId);
        sources.descriptions().forget(mediaId);
        store.removeFinding(Finding.key(HealthCheck.ORPHAN_MEDIA, Finding.Subject.MEDIA, mediaId, ""));
        store.removeFinding(Finding.key(HealthCheck.MISSING_ALT, Finding.Subject.MEDIA, mediaId, ""));
        log.info("Content health: orphan medium {} ({}) deleted by {}", mediaId, asset.title(), reader.userId());
    }

    public boolean altTextAvailable() {
        return ai.available();
    }

    /**
     * A text alternative for an image of the library, written by the AI from the image: a suggestion — the
     * editor reads it, changes it if needed, and applies it ({@link #applyAlt}).
     */
    public String suggestAlt(Reader reader, String mediaId, String authorization) {
        reader.require(EditorialPermission.EDIT);
        var asset = sources.media().find(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Ese medio ya no existe"));
        if (asset.kind() != MediaKind.IMAGE) {
            throw new IllegalArgumentException("Solo para imágenes de la biblioteca");
        }
        var bytes = sources.media().bytes(mediaId)
                .orElseThrow(() -> new IllegalStateException("La imagen no tiene fichero guardado"));
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("La imagen ocupa más de 5 MB: la IA no la acepta; escribe el texto a mano");
        }
        var usedIn = snapshot().items().stream()
                .filter(i -> io.mateu.ecdemo1.contenthub.domain.media.MediaReferences.in(i.draft()).contains(mediaId))
                .map(ContentSnapshot::title).limit(3).toList();
        var context = "Título del medio: " + asset.title()
                + (usedIn.isEmpty() ? "" : ". Se usa en: " + String.join("; ", usedIn));
        return clean(ai.suggest(bytes, asset.contentType(), context, authorization));
    }

    /** The editor's text alternative (the AI's, reviewed, or their own) for an image of the library. */
    public void applyAlt(Reader reader, String mediaId, String altText) {
        reader.require(EditorialPermission.EDIT);
        var asset = sources.media().find(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Ese medio ya no existe"));
        if (asset.kind() != MediaKind.IMAGE) {
            throw new IllegalArgumentException("Solo las imágenes llevan texto alternativo");
        }
        var text = clean(altText);
        if (text.isBlank()) {
            throw new IllegalArgumentException("Escribe el texto alternativo (o pide uno a la IA)");
        }
        sources.descriptions().describe(mediaId, text, reader.userId(), clock.instant());
        store.removeFinding(Finding.key(HealthCheck.MISSING_ALT, Finding.Subject.MEDIA, mediaId, ""));
    }

    /** One line of plain text, no quotes around it, no longer than {@link #MAX_ALT}. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        var plain = Jsoup.parse(text).text().replaceAll("\\s+", " ").trim();
        plain = plain.replaceFirst("^(?i)(texto alternativo|alt)\\s*:\\s*", "");
        while (plain.length() >= 2 && "\"'«“".indexOf(plain.charAt(0)) >= 0 && "\"'»”".indexOf(plain.charAt(plain.length() - 1)) >= 0) {
            plain = plain.substring(1, plain.length() - 1).trim();
        }
        if (plain.length() > MAX_ALT) {
            var cut = plain.substring(0, MAX_ALT);
            var space = cut.lastIndexOf(' ');
            plain = (space > MAX_ALT / 2 ? cut.substring(0, space) : cut).trim();
        }
        return plain;
    }
}
