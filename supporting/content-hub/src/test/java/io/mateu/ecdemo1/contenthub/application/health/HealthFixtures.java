package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.ecdemo1.contenthub.application.out.HealthRepository;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.health.Finding;
import io.mateu.ecdemo1.contenthub.domain.health.LinkStatus;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.domain.media.MediaKind;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Content to check, built through the domain as editors would, and a store in memory. */
final class HealthFixtures {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final String HOST = "ec1.localhost";

    private HealthFixtures() {
    }

    static ItemContent content(String section, String body, List<Block> blocks, Instant expireAt) {
        return new ItemContent(section, Audience.EVERYONE, Map.of("es", new Translation("T", "", body)), blocks, null,
                expireAt, false, false, null, null);
    }

    static ItemContent titled(String title, String section, String body, List<Block> blocks, Instant expireAt) {
        return new ItemContent(section, Audience.EVERYONE, Map.of("es", new Translation(title, "", body)), blocks, null,
                expireAt, false, false, null, null);
    }

    /** Published at {@code at}. */
    static Item published(String id, ItemType type, ItemContent content, Instant at) {
        var item = Item.create(id, type, "eva", "es", content, at);
        item.publish("pablo", true, false, at);
        return item;
    }

    static Item published(String id, String title, String body) {
        return published(id, ItemType.ARTICLE, titled(title, "s1", body, List.of(), null), NOW.minus(Duration.ofDays(3)));
    }

    static Item page(String id, String title, List<Block> blocks) {
        return published(id, ItemType.PAGE, titled(title, "s1", "", blocks, null), NOW.minus(Duration.ofDays(3)));
    }

    static Item draft(String id, String title, String body) {
        return Item.create(id, ItemType.ARTICLE, "eva", "es", titled(title, "s1", body, List.of(), null), NOW);
    }

    static MediaAsset image(String id, long size, Instant uploadedAt) {
        return new MediaAsset(id, MediaKind.IMAGE, "Foto " + id, id + ".jpg", "image/jpeg", size, null, "eva", uploadedAt);
    }

    static Snapshot snapshot() {
        return new Snapshot();
    }

    /** A snapshot under construction: everything empty but section s1, the portal host and the clock. */
    static final class Snapshot {
        final List<Item> items = new ArrayList<>();
        final List<MediaAsset> media = new ArrayList<>();
        final Map<String, String> alts = new HashMap<>();
        final List<Slot> slots = new ArrayList<>();
        final List<String> homes = new ArrayList<>();
        final Map<String, List<String>> otherHomes = new HashMap<>();
        final List<SiteMenu> menus = new ArrayList<>();
        final List<io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue> issues = new ArrayList<>();
        final Map<String, Boolean> uiSources = new HashMap<>(Map.of("crs", true));
        final List<io.mateu.ecdemo1.contenthub.domain.template.PageTemplate> templates = new ArrayList<>();
        Set<String> viewed = Set.of();
        Instant analyticsSince;
        Instant now = NOW;

        Snapshot item(Item i) {
            items.add(i);
            return this;
        }

        Snapshot media(MediaAsset a) {
            media.add(a);
            return this;
        }

        ContentSnapshot build() {
            var allHomes = new HashMap<>(otherHomes);
            allHomes.put("intranet", homes);
            return new ContentSnapshot(now, items, media, alts, slots, Map.of("intranet", "Intranet", "boletin", "Boletín"),
                    allHomes, Map.of("intranet", Set.of("s1", "s2"), "boletin", Set.of("nl1")), menus, issues, Map.of(),
                    templates, uiSources, Set.of("ana.perez"), Set.of("enc1"), Set.of(HOST), viewed, analyticsSince);
        }
    }

    /** {@link HealthRepository} in memory, as the service and the checker use it. */
    static final class InMemoryHealth implements HealthRepository {
        final Map<String, Run> runs = new LinkedHashMap<>();
        final Map<LocalDate, String> nights = new HashMap<>();
        final Map<String, StoredFinding> findings = new LinkedHashMap<>();
        final Map<String, Snooze> snoozes = new LinkedHashMap<>();
        final Map<String, LinkStatus> links = new HashMap<>();
        String last;

        @Override
        public synchronized boolean claimNightly(LocalDate day, String runId, Instant now) {
            if (nights.putIfAbsent(day, runId) != null) {
                return false;
            }
            startRun(runId, "NIGHTLY", "sistema", now);
            return true;
        }

        @Override
        public void startRun(String runId, String trigger, String by, Instant now) {
            runs.put(runId, new Run(runId, trigger, by, now, null, Map.of(), null));
            last = runId;
        }

        @Override
        public void finishRun(String runId, Map<String, Integer> counts, String error, Instant now) {
            var r = runs.get(runId);
            runs.put(runId, new Run(runId, r.trigger(), r.by(), r.startedAt(), now, counts, error));
        }

        @Override
        public Optional<Run> lastRun() {
            return Optional.ofNullable(last).map(runs::get);
        }

        @Override
        public void replaceFindings(List<Finding> list, Instant now) {
            var next = new LinkedHashMap<String, StoredFinding>();
            for (var f : list) {
                var old = findings.get(f.key());
                next.put(f.key(), new StoredFinding(f, old == null ? now : old.firstSeen(), now));
            }
            findings.clear();
            findings.putAll(next);
            snoozes.keySet().retainAll(findings.keySet());
        }

        @Override
        public List<StoredFinding> findings() {
            return List.copyOf(findings.values());
        }

        @Override
        public Optional<StoredFinding> finding(String key) {
            return Optional.ofNullable(findings.get(key));
        }

        @Override
        public void removeFinding(String key) {
            findings.remove(key);
            snoozes.remove(key);
        }

        @Override
        public Map<String, Snooze> snoozes() {
            return Map.copyOf(snoozes);
        }

        @Override
        public void snooze(Snooze snooze) {
            snoozes.put(snooze.key(), snooze);
        }

        @Override
        public void unsnooze(String key) {
            snoozes.remove(key);
        }

        @Override
        public Optional<LinkStatus> cachedLink(String url) {
            return Optional.ofNullable(links.get(url));
        }

        @Override
        public void cacheLink(LinkStatus status) {
            links.put(status.url(), status);
        }
    }
}
