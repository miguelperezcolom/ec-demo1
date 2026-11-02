package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.ecdemo1.contenthub.application.health.ContentReferences.External;
import io.mateu.ecdemo1.contenthub.application.health.ContentReferences.Internal;
import io.mateu.ecdemo1.contenthub.application.health.ContentReferences.Kind;
import io.mateu.ecdemo1.contenthub.application.health.ContentReferences.Malformed;
import io.mateu.ecdemo1.contenthub.application.health.ContentReferences.Reference;
import io.mateu.ecdemo1.contenthub.application.health.ContentReferences.SignedMedia;
import io.mateu.ecdemo1.contenthub.application.health.ContentSnapshot.ItemState;
import io.mateu.ecdemo1.contenthub.application.menu.PortalFunction;
import io.mateu.ecdemo1.contenthub.domain.health.Finding;
import io.mateu.ecdemo1.contenthub.domain.health.Finding.Fix;
import io.mateu.ecdemo1.contenthub.domain.health.Finding.Subject;
import io.mateu.ecdemo1.contenthub.domain.health.HealthCheck;
import io.mateu.ecdemo1.contenthub.domain.health.LinkStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.domain.media.MediaKind;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The five checks, as pure functions of a {@link ContentSnapshot} (and, for the external links, of what
 * each URL answered): no I/O here, so each rule is tested on its own.
 */
public final class HealthChecks {

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZONE);
    /** "Nadie la ha abierto" needs at least this much analytics to mean anything. */
    static final Duration MIN_UNVIEWED_WINDOW = Duration.ofDays(14);

    private HealthChecks() {
    }

    public static List<Finding> all(ContentSnapshot s, Map<String, LinkStatus> external, HealthSettings settings) {
        var refs = ContentReferences.live(s);
        var out = new ArrayList<Finding>();
        out.addAll(brokenLinks(s, refs, external));
        out.addAll(orphanMedia(s, settings));
        out.addAll(missingAlt(s, refs));
        out.addAll(expiry(s, refs, settings));
        out.addAll(stale(s, settings));
        return out;
    }

    /** The external URLs live content uses: what the link checker is asked. */
    public static Set<String> externalUrls(ContentSnapshot s) {
        var out = new java.util.LinkedHashSet<String>();
        for (var r : ContentReferences.live(s)) {
            if ((r.kind() == Kind.HREF || r.kind() == Kind.IMG)
                    && ContentReferences.classify(r.value(), s.internalHosts()) instanceof External e) {
                out.add(e.url());
            }
        }
        return out;
    }

    // ---- 1. broken links ----------------------------------------------------------------------------------

    public static List<Finding> brokenLinks(ContentSnapshot s, List<Reference> refs, Map<String, LinkStatus> external) {
        var media = mediaById(s);
        var out = new LinkedHashMap<String, Finding>();
        for (var r : refs) {
            var problem = switch (r.kind()) {
                case HREF, IMG -> switch (ContentReferences.classify(r.value(), s.internalHosts())) {
                    case Malformed m -> Optional.of(m.reason());
                    case SignedMedia sm -> Optional.of(media.containsKey(sm.mediaId())
                            ? "es la URL firmada de un medio, que deja de funcionar en unas horas: usa [[media:" + sm.mediaId() + "]]"
                            : "el medio «" + sm.mediaId() + "» no existe");
                    case Internal in -> internal(s, in.path()).filter(p -> !p.startsWith(EXPIRED));
                    case External e -> Optional.ofNullable(external.get(e.url()))
                            .filter(st -> st.outcome() == LinkStatus.Outcome.BROKEN)
                            .map(st -> "el enlace externo falla (" + st.detail() + ")");
                    default -> Optional.<String>empty();
                };
                case MEDIA -> media.containsKey(r.value()) ? Optional.<String>empty()
                        : Optional.of("el medio «" + r.value() + "» no existe");
                case ITEM -> itemProblem(s, r.value());
                case SECTION -> s.section(r.siteId(), r.value()) ? Optional.<String>empty()
                        : Optional.of("la sección «" + r.value() + "» no existe en «" + s.siteName(Site.of(r.siteId())) + "»");
                case BUILTIN -> PortalFunction.byKey(r.value()).isPresent() ? Optional.<String>empty()
                        : Optional.of("la función del portal «" + r.value() + "» no existe");
                case REMOTE -> s.uiSources().containsKey(r.value())
                        ? (s.uiSources().get(r.value()) ? Optional.<String>empty()
                        : Optional.of("la UI externa «" + r.value() + "» está desactivada: nadie la ve"))
                        : Optional.of("la UI externa «" + r.value() + "» no está (o ya no está) en el registro de UIs");
                case ISSUE -> issueProblem(s, r.value());
                case IMG_NO_ALT -> Optional.<String>empty();
            };
            problem.ifPresent(p -> add(out, HealthCheck.BROKEN_LINKS, r, r.value(), describe(r) + ": " + p));
        }
        return List.copyOf(out.values());
    }

    static final String EXPIRED = "EXPIRED:";

    /** What is wrong with a portal path, if anything. An expired item comes back as {@link #EXPIRED} + its id. */
    static Optional<String> internal(ContentSnapshot s, String path) {
        if (!path.equals("/intranet") && !path.startsWith("/intranet/")) {
            return Optional.empty(); // the root, the backoffice, another application of the platform
        }
        if (path.equals("/intranet")) {
            return Optional.empty();
        }
        var parts = path.substring("/intranet/".length()).split("/");
        var missing = Optional.of("la página " + path + " no existe en el portal");
        if (parts[0].equals("n") && parts.length >= 2) {
            return newsletterPath(s, path, parts);
        }
        return switch (parts.length) {
            case 1 -> PortalFunction.byKey(parts[0]).isPresent() ? Optional.empty() : missing;
            case 2 -> switch (parts[0]) {
                case "noticias" -> s.itemState(parts[1]) == ItemState.EXPIRED ? Optional.of(EXPIRED + parts[1])
                        : itemProblem(s, parts[1]);
                case "secciones" -> s.section(Site.INTRANET, parts[1]) ? Optional.empty()
                        : Optional.of("la sección «" + parts[1] + "» no existe");
                case "personas" -> s.personIds().contains(parts[1]) ? Optional.empty()
                        : Optional.of("no hay nadie con el id «" + parts[1] + "» en el directorio");
                case "encuestas" -> s.surveyIds().contains(parts[1]) ? Optional.empty()
                        : Optional.of("la encuesta «" + parts[1] + "» no existe");
                case "solicitudes" -> Optional.empty(); // someone's own request: not for us to look into
                default -> missing;
            };
            case 3 -> (parts[0].equals("enlace") || parts[0].equals("ui"))
                    && s.menus().stream().anyMatch(m -> m.siteId().equals(parts[1]) && m.find(parts[2]).isPresent())
                    ? Optional.empty() : missing;
            default -> missing;
        };
    }

    static Optional<String> issueProblem(ContentSnapshot s, String id) {
        var issue = s.issue(id).orElse(null);
        if (issue == null) {
            return Optional.of("el número «" + id + "» no existe");
        }
        return issue.status() == NewsletterIssue.Status.SENT ? Optional.empty()
                : Optional.of("el número " + issue.number() + " aún no se ha enviado: no tiene página");
    }

    /** A newsletter site's route: /intranet/n/{site}[/p/{id} | /secciones/{id} | /archivo | /numeros/{id}]. */
    static Optional<String> newsletterPath(ContentSnapshot s, String path, String[] parts) {
        var site = parts[1];
        if (!s.sites().containsKey(site)) {
            return Optional.of("el sitio «" + site + "» no existe");
        }
        if (parts.length == 2 || (parts.length == 3 && parts[2].equals("archivo"))) {
            return Optional.empty();
        }
        if (parts.length != 4) {
            return Optional.of("la página " + path + " no existe en el portal");
        }
        return switch (parts[2]) {
            case "p" -> s.itemState(parts[3]) == ItemState.EXPIRED ? Optional.of(EXPIRED + parts[3]) : itemProblem(s, parts[3]);
            case "secciones" -> s.section(site, parts[3]) ? Optional.empty()
                    : Optional.of("la sección «" + parts[3] + "» no existe en «" + s.siteName(site) + "»");
            case "numeros" -> issueProblem(s, parts[3]);
            default -> Optional.of("la página " + path + " no existe en el portal");
        };
    }

    static Optional<String> itemProblem(ContentSnapshot s, String id) {
        return switch (s.itemState(id)) {
            case MISSING -> Optional.of("el contenido «" + id + "» no existe");
            case UNPUBLISHED -> Optional.of("«" + titleOf(s, id) + "» no está publicado");
            case RETIRED -> Optional.of("«" + titleOf(s, id) + "» está retirado");
            case EXPIRED, OK -> Optional.empty(); // expired: the expiry check says it, with its date
        };
    }

    // ---- 2. orphan media ----------------------------------------------------------------------------------

    public static List<Finding> orphanMedia(ContentSnapshot s, HealthSettings settings) {
        var inUse = ContentReferences.mediaInUse(s);
        var out = new ArrayList<Finding>();
        for (var a : s.media()) {
            if (inUse.contains(a.id())
                    || (a.uploadedAt() != null && a.uploadedAt().isAfter(s.now().minus(settings.orphanGrace())))) {
                continue;
            }
            var what = a.kind() == MediaKind.EXTERNAL ? "enlace externo" : size(a.size());
            out.add(Finding.of(HealthCheck.ORPHAN_MEDIA, Subject.MEDIA, a.id(), a.title(), "",
                    "Ningún contenido, bloque, slot ni newsletter lo usa · " + what + " · subido el "
                            + day(a.uploadedAt()) + (a.uploadedBy() == null ? "" : " por " + a.uploadedBy()),
                    Fix.DELETE_MEDIA).withSize(a.size()));
        }
        return out;
    }

    public static boolean orphan(ContentSnapshot s, String mediaId) {
        return !ContentReferences.mediaInUse(s).contains(mediaId);
    }

    // ---- 3. images without a text alternative -------------------------------------------------------------

    public static List<Finding> missingAlt(ContentSnapshot s, List<Reference> refs) {
        var out = new LinkedHashMap<String, Finding>();
        var users = new HashMap<String, Set<String>>();
        for (var r : refs) {
            if (r.kind() == Kind.MEDIA) {
                users.computeIfAbsent(r.value(), k -> new HashSet<>()).add(r.subjectId());
            }
        }
        for (var a : s.media()) {
            var alt = s.altTexts().get(a.id());
            if (a.kind() == MediaKind.IMAGE && (alt == null || alt.isBlank())) {
                var n = users.getOrDefault(a.id(), Set.of()).size();
                var f = Finding.of(HealthCheck.MISSING_ALT, Subject.MEDIA, a.id(), a.title(), "",
                        "Imagen de la biblioteca sin texto alternativo (se lee su título, «" + a.title() + "») · "
                                + (n == 0 ? "no se ve en nada publicado" : "se ve en " + n + (n == 1 ? " sitio" : " sitios")),
                        Fix.SUGGEST_ALT);
                out.put(f.key(), f);
            }
        }
        for (var r : refs) {
            if (r.kind() == Kind.IMG_NO_ALT) {
                add(out, HealthCheck.MISSING_ALT, r, r.value(), "Imagen sin texto alternativo (atributo alt) en "
                        + r.where() + ": " + shorten(r.value()));
            }
        }
        return List.copyOf(out.values());
    }

    // ---- 4. expiry ----------------------------------------------------------------------------------------

    public static List<Finding> expiry(ContentSnapshot s, List<Reference> refs, HealthSettings settings) {
        var out = new LinkedHashMap<String, Finding>();
        var horizon = s.now().plus(Duration.ofDays(settings.expiryDays()));
        for (var item : s.items()) {
            if (item.status() != ItemStatus.PUBLISHED) {
                continue;
            }
            var expireAt = item.publishedVersion().map(v -> v.content().expireAt()).orElse(null);
            if (expireAt != null && expireAt.isAfter(s.now()) && !expireAt.isAfter(horizon)) {
                var days = Math.max(0, Duration.between(s.now(), expireAt).toDays());
                var f = Finding.of(HealthCheck.EXPIRY, Subject.ITEM, item.id(), ContentSnapshot.title(item), "expira",
                        "Caduca el " + day(expireAt) + (days == 0 ? " (hoy)" : " (en " + days + (days == 1 ? " día)" : " días)"))
                                + ": amplía la fecha si debe seguir viéndose", Fix.EDIT_ITEM).inSite(item.siteId());
                out.put(f.key(), f);
            }
        }
        for (var r : refs) {
            String expiredId = null;
            if (r.kind() == Kind.ITEM && s.itemState(r.value()) == ItemState.EXPIRED) {
                expiredId = r.value();
            } else if (r.kind() == Kind.HREF
                    && ContentReferences.classify(r.value(), s.internalHosts()) instanceof Internal in) {
                expiredId = internal(s, in.path()).filter(p -> p.startsWith(EXPIRED))
                        .map(p -> p.substring(EXPIRED.length())).orElse(null);
            }
            if (expiredId != null) {
                var expired = s.item(expiredId).orElseThrow();
                var at = expired.publishedVersion().map(v -> v.content().expireAt()).orElse(null);
                add(out, HealthCheck.EXPIRY, r, "item:" + expiredId, describe(r) + ": enlaza a «"
                        + ContentSnapshot.title(expired) + "», que caducó el " + day(at) + " y ya no se ve");
            }
        }
        return List.copyOf(out.values());
    }

    // ---- 5. stale -----------------------------------------------------------------------------------------

    public static List<Finding> stale(ContentSnapshot s, HealthSettings settings) {
        var out = new ArrayList<Finding>();
        var limit = s.now().atZone(ZONE).minusMonths(settings.staleMonths()).toInstant();
        var window = unviewedWindowStart(s.now(), s.analyticsSince(), settings);
        for (var item : s.items()) {
            var content = s.live(item).orElse(null);
            if (content == null) {
                continue;
            }
            var last = item.latestVersion().map(v -> v.publishedAt()).orElse(null);
            if (last != null && last.isBefore(limit) && settings.freshnessMatters(content.sectionId())) {
                out.add(Finding.of(HealthCheck.STALE, Subject.ITEM, item.id(), ContentSnapshot.title(item), "sin-actualizar",
                        "Publicado por última vez el " + day(last) + ", hace más de " + settings.staleMonths()
                                + " meses: revisa si sigue siendo cierto", Fix.EDIT_ITEM).inSite(item.siteId()));
            }
            var first = item.versions().isEmpty() ? null : item.versions().getFirst().publishedAt();
            if (window.isPresent() && item.type() == ItemType.PAGE && first != null && !first.isAfter(window.get())
                    && !s.viewedItemIds().contains(item.id()) && s.homes().values().stream().noneMatch(h -> h.contains(item.id()))) {
                out.add(Finding.of(HealthCheck.STALE, Subject.ITEM, item.id(), ContentSnapshot.title(item), "sin-visitas",
                        "Nadie ha abierto esta página desde el " + day(window.get())
                                + ": ¿sigue haciendo falta, o no se encuentra?", Fix.EDIT_ITEM).inSite(item.siteId()));
            }
        }
        return out;
    }

    /**
     * Where "nobody opened it" starts counting: the last {@code unviewedDays}, or since analytics began if
     * that is later — and nothing at all without analytics or with less than two weeks of them.
     */
    public static Optional<Instant> unviewedWindowStart(Instant now, Instant analyticsSince, HealthSettings settings) {
        if (analyticsSince == null) {
            return Optional.empty();
        }
        var start = now.minus(Duration.ofDays(settings.unviewedDays()));
        if (analyticsSince.isAfter(start)) {
            start = analyticsSince;
        }
        return Duration.between(start, now).compareTo(MIN_UNVIEWED_WINDOW) >= 0 ? Optional.of(start) : Optional.empty();
    }

    // ---- shared -------------------------------------------------------------------------------------------

    static void add(Map<String, Finding> out, HealthCheck check, Reference r, String target, String problem) {
        var fix = switch (r.subject()) {
            case ITEM -> Fix.EDIT_ITEM;
            case ISSUE -> Fix.EDIT_ISSUE;
            default -> Fix.CONFIGURE;
        };
        var f = Finding.of(check, r.subject(), r.subjectId(), r.subjectTitle(), target, problem, fix)
                .inSite(r.siteId() == null ? null : Site.of(r.siteId()));
        out.merge(f.key(), f, (a, b) -> a.problem().contains(r.where()) ? a
                : new Finding(a.key(), a.check(), a.subject(), a.subjectId(), a.subjectTitle(), a.target(),
                a.problem() + "; también en " + r.where(), a.fix(), a.size(), a.siteId()));
    }

    static String describe(Reference r) {
        var what = switch (r.kind()) {
            case HREF -> "Enlace a " + shorten(r.value());
            case IMG, IMG_NO_ALT -> "Imagen " + shorten(r.value());
            case MEDIA -> "Medio [[media:" + r.value() + "]]";
            case ITEM -> "Contenido «" + r.value() + "»";
            case SECTION -> "Sección «" + r.value() + "»";
            case BUILTIN -> "Función «" + r.value() + "»";
            case REMOTE -> "UI externa «" + r.value() + "»";
            case ISSUE -> "Número «" + r.value() + "»";
        };
        return what + " (" + r.where() + ")";
    }

    static Map<String, MediaAsset> mediaById(ContentSnapshot s) {
        return s.media().stream().collect(Collectors.toMap(MediaAsset::id, Function.identity(), (a, b) -> a));
    }

    static String titleOf(ContentSnapshot s, String id) {
        return s.item(id).map(ContentSnapshot::title).orElse(id);
    }

    static String day(Instant at) {
        return at == null ? "?" : DAY.format(at);
    }

    static String size(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.of("es"), "%.1f MB", bytes / 1024.0 / 1024.0);
        }
        return Math.max(1, bytes / 1024) + " KB";
    }

    static String shorten(String url) {
        return url == null ? "" : url.length() <= 90 ? url : url.substring(0, 87) + "…";
    }
}
