package io.mateu.ecdemo1.contenthub.domain.site;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A site content-hub hosts. The intranet is the default one ({@link #INTRANET}); every newsletter is
 * another, of kind {@link Kind#NEWSLETTER}, with the same machinery scoped to it: its own section tree,
 * its own items and pages, its homes. A site has an identity (name, logo, colours, header and footer), an
 * audience — who it is for — and the languages it is written in.
 *
 * <p>Everything that existed before sites belongs to the intranet: an item, a section or a home with no
 * site is the intranet's.
 */
public class Site extends AggregateRoot {

    public static final String INTRANET = "intranet";

    public enum Kind { INTRANET, NEWSLETTER }

    /** Its colours: the header's background and the links'. */
    public record Theme(String primary, String accent) {
        static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
        public static final Theme DEFAULT = new Theme("#1d5c7a", "#2f8f8f");

        public Theme {
            primary = color(primary, "#1d5c7a");
            accent = color(accent, "#2f8f8f");
        }

        static String color(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            if (!HEX.matcher(value.trim()).matches()) {
                throw new IllegalArgumentException("Un color es #RRGGBB: «" + value + "»");
            }
            return value.trim().toLowerCase();
        }
    }

    /** What an editor sets. */
    public record Definition(String name, String description, Audience audience, List<String> languages,
                             String logoMediaId, Theme theme, String headerHtml, String footerHtml, boolean active) {
        public Definition {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("El sitio necesita un nombre");
            }
            name = name.trim();
            description = description == null ? "" : description;
            audience = audience == null ? Audience.EVERYONE : audience;
            languages = languages == null ? List.of() : List.copyOf(languages);
            logoMediaId = logoMediaId == null || logoMediaId.isBlank() ? null : logoMediaId;
            theme = theme == null ? Theme.DEFAULT : theme;
            headerHtml = headerHtml == null ? "" : headerHtml;
            footerHtml = footerHtml == null ? "" : footerHtml;
        }
    }

    static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,48}");

    private String id;
    private Kind kind;
    private Definition definition;

    @SuppressWarnings("unused") // for the store
    private Site() {
    }

    public static Site create(String id, Kind kind, Definition definition) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("El id de un sitio son letras minúsculas, cifras y guiones: «" + id + "»");
        }
        if (kind == Kind.NEWSLETTER && INTRANET.equals(id)) {
            throw new IllegalArgumentException("«intranet» es el sitio de la intranet");
        }
        var s = new Site();
        s.id = id;
        s.kind = kind == null ? Kind.NEWSLETTER : kind;
        s.definition = definition;
        return s;
    }

    /** The intranet as a site, when nobody has given it an identity of its own yet. */
    public static Site intranet() {
        return create(INTRANET, Kind.INTRANET, new Definition("Intranet", "", Audience.EVERYONE, List.of(), null,
                Theme.DEFAULT, "", "", true));
    }

    /** An id for a new site, from its name: {@code boletin-de-rrhh-1a2b}. */
    public static String newId(String name) {
        var slug = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        slug = slug.isEmpty() ? "sitio" : slug.length() > 30 ? slug.substring(0, 30).replaceAll("-$", "") : slug;
        return slug + "-" + UUID.randomUUID().toString().substring(0, 4);
    }

    /** The site an item, section or home belongs to: the intranet when it says none. */
    public static String of(String siteId) {
        return siteId == null || siteId.isBlank() ? INTRANET : siteId;
    }

    /** The language it is written in first — its blocks' default language, what a reader of another gets. */
    public String defaultLanguage() {
        var languages = definition.languages();
        return languages.isEmpty() ? "es" : languages.getFirst();
    }

    public void configure(Definition definition) {
        this.definition = definition;
    }

    /** The language a reader gets it in: theirs, if the site is written in it; else its first one. */
    public String languageFor(String readerLanguage) {
        var languages = definition.languages();
        return languages.isEmpty() || languages.contains(readerLanguage) ? readerLanguage : languages.getFirst();
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public Definition definition() {
        return definition;
    }

    public String name() {
        return definition.name();
    }

    public Audience audience() {
        return definition.audience();
    }

    public boolean active() {
        return definition.active();
    }
}
