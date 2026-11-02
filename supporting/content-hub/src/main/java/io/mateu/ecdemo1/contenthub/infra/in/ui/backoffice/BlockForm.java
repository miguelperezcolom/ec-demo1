package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.VisibilitySupplier;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/**
 * One block of a page, as the editor fills it in. Its audience can only narrow the page's: whatever is
 * chosen here is ANDed with the page's audience. Only the fields its type uses are shown: an HTML block
 * is a title and its content (rich text, no HTML to type), a listing its section and how many…
 */
@Editors
public class BlockForm implements OptionsSupplier, VisibilitySupplier {

    @Label("Tipo")
    public BlockType type = BlockType.HTML;
    // its text in each language, as an item's (Español / English / Deutsch); the default language's is what a
    // reader of a language without its own gets
    @Label("Título (Español)")
    public String titleEs;
    @HiddenInList
    @Label("Contenido (Español; incrusta medios con [[media:id]])")
    @Stereotype(FieldStereotype.richText)
    public String htmlEs;
    @HiddenInList
    @Label("Title (English)")
    public String titleEn;
    @HiddenInList
    @Label("Content (English)")
    @Stereotype(FieldStereotype.richText)
    public String htmlEn;
    @HiddenInList
    @Label("Titel (Deutsch)")
    public String titleDe;
    @HiddenInList
    @Label("Inhalt (Deutsch)")
    @Stereotype(FieldStereotype.richText)
    public String htmlDe;
    @HiddenInList
    @Label("Item destacado")
    @Stereotype(FieldStereotype.select)
    public String itemId;
    @HiddenInList
    @Label("Sección listada (vacío: todas)")
    @Stereotype(FieldStereotype.select)
    public String sectionId;
    @HiddenInList
    @Label("Cuántos")
    public Integer limit;
    @HiddenInList
    @Label("Enlaces")
    public List<LinkForm> links = new java.util.ArrayList<>();
    @HiddenInList
    @Label("Medio de la biblioteca")
    @Stereotype(FieldStereotype.select)
    public String mediaId;
    @HiddenInList
    @Label("UI externa")
    @Stereotype(FieldStereotype.select)
    public String remoteSourceId;
    @HiddenInList
    @Label("Ruta (una UI de Mateu o un iframe: /loyalty/members…)")
    public String remoteRoute;
    @HiddenInList
    @Label("Atributos (un web component: nombre=valor, uno por línea)")
    @Stereotype(FieldStereotype.textarea)
    public String remoteAttributes;
    @HiddenInList
    @Label("Solo estos departamentos")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> blockDepartments;
    @HiddenInList
    @Label("Solo estos hoteles")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> blockHotels;
    @HiddenInList
    @Label("Solo estos puestos")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> blockJobTitles;
    @HiddenInList
    @Label("Id")
    public String id;

    /** The languages a block's text is written in, as an item's. */
    public static final List<String> LANGUAGES = List.of("es", "en", "de");

    /**
     * @param defaultLanguage the language of the block's own text (its page's, its newsletter's): its fields are
     *                        that language's; each translation fills its own
     */
    public static BlockForm of(Block b, String defaultLanguage) {
        var f = new BlockForm();
        f.id = b.id();
        f.type = b.type();
        f.itemId = b.itemId();
        f.sectionId = b.sectionId();
        f.limit = b.limit();
        f.mediaId = b.mediaId();
        f.links = new java.util.ArrayList<>(b.links().stream().map(l -> {
            var link = new LinkForm();
            link.url = l.url();
            return link;
        }).toList());
        for (var language : LANGUAGES) {
            var text = b.textIn(language, defaultLanguage);
            f.title(language, text.title());
            f.html(language, text.html());
            for (var link : f.links) {
                link.label(language, text.labels().get(link.url));
            }
        }
        if (b.remote() != null) {
            f.remoteSourceId = b.remote().sourceId();
            f.remoteRoute = b.remote().route();
            f.remoteAttributes = b.remote().attributeLines();
        }
        var parsed = AudienceFields.parse(b.audience());
        f.blockDepartments = parsed.values().get(Attribute.DEPARTMENT);
        f.blockHotels = parsed.values().get(Attribute.HOTEL);
        f.blockJobTitles = parsed.values().get(Attribute.JOB_TITLE);
        return f;
    }

    /** The block, its own text {@code defaultLanguage}'s and the other languages' as its translations. */
    public Block toBlock(String defaultLanguage) {
        var values = new EnumMap<Attribute, List<String>>(Attribute.class);
        values.put(Attribute.DEPARTMENT, blockDepartments);
        values.put(Attribute.HOTEL, blockHotels);
        values.put(Attribute.JOB_TITLE, blockJobTitles);
        Audience audience = AudienceFields.build(null, values, null);
        var withUrl = links == null ? List.<LinkForm>of() : links.stream()
                .filter(l -> l != null && l.url != null && !l.url.isBlank()).toList();
        var parsedLinks = withUrl.stream()
                .map(l -> new Block.Link(blank(l.label(defaultLanguage)) == null ? l.url.trim() : l.label(defaultLanguage).trim(),
                        l.url.trim()))
                .toList();
        var translations = new java.util.LinkedHashMap<String, Block.BlockText>();
        for (var language : LANGUAGES) {
            if (language.equals(defaultLanguage)) {
                continue;
            }
            var labels = new java.util.LinkedHashMap<String, String>();
            withUrl.forEach(l -> labels.putIfAbsent(l.url.trim(), l.label(language)));
            translations.put(language, new Block.BlockText(title(language), html(language), labels));
        }
        var t = type == null ? BlockType.HTML : type;
        RemoteUi remote = null;
        if (t == BlockType.REMOTE) {
            if (blank(remoteSourceId) == null) {
                throw new IllegalArgumentException("Elige la UI externa del componente remoto");
            }
            remote = new RemoteUi(blank(remoteSourceId), remoteRoute, RemoteUi.attributesOf(remoteAttributes));
        }
        return new Block(id == null || id.isBlank() ? "b" + UUID.randomUUID().toString().substring(0, 6) : id,
                t, audience, title(defaultLanguage), html(defaultLanguage), blank(itemId), blank(sectionId), limit,
                parsedLinks, blank(mediaId), remote, translations);
    }

    public String title(String language) {
        return switch (language == null ? "es" : language) {
            case "en" -> titleEn;
            case "de" -> titleDe;
            default -> titleEs;
        };
    }

    public void title(String language, String title) {
        switch (language == null ? "es" : language) {
            case "en" -> titleEn = title;
            case "de" -> titleDe = title;
            default -> titleEs = title;
        }
    }

    public String html(String language) {
        return switch (language == null ? "es" : language) {
            case "en" -> htmlEn;
            case "de" -> htmlDe;
            default -> htmlEs;
        };
    }

    public void html(String language, String html) {
        switch (language == null ? "es" : language) {
            case "en" -> htmlEn = html;
            case "de" -> htmlDe = html;
            default -> htmlEs = html;
        }
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "itemId".equals(fieldName) || "sectionId".equals(fieldName) || "mediaId".equals(fieldName)
                || "remoteSourceId".equals(fieldName) || AudienceFields.supports(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "itemId" -> Catalog.get().publishedItems();
            // the intranet's sections, and the newsletters' (nl:<section>) for a newsletter section block
            case "sectionId" -> Catalog.get().sectionsForBlocks();
            case "mediaId" -> Catalog.get().media();
            case "remoteSourceId" -> Catalog.get().uiSources();
            default -> AudienceFields.options(fieldName);
        };
    }

    /** The fields each type uses (what PageRenderer reads); the title and the audience, always. */
    static final java.util.Map<BlockType, java.util.Set<String>> FIELDS_OF = java.util.Map.ofEntries(
            java.util.Map.entry(BlockType.HTML, java.util.Set.of("html")),
            java.util.Map.entry(BlockType.BANNER, java.util.Set.of("html")),
            java.util.Map.entry(BlockType.FEATURED, java.util.Set.of("itemId")),
            java.util.Map.entry(BlockType.AUTOMATIC_LISTING, java.util.Set.of("sectionId", "limit")),
            java.util.Map.entry(BlockType.QUICK_LINKS, java.util.Set.of("links")),
            java.util.Map.entry(BlockType.MEDIA, java.util.Set.of("mediaId")),
            java.util.Map.entry(BlockType.GREETING, java.util.Set.of()),
            java.util.Map.entry(BlockType.PENDING, java.util.Set.of()),
            java.util.Map.entry(BlockType.NOTICES, java.util.Set.of("limit")),
            java.util.Map.entry(BlockType.LATEST, java.util.Set.of("sectionId", "limit")),
            java.util.Map.entry(BlockType.REMOTE, java.util.Set.of("remoteSourceId", "remoteRoute", "remoteAttributes")),
            java.util.Map.entry(BlockType.NEWSLETTER_SECTION, java.util.Set.of("sectionId")));

    /** The type's own fields, in the form's order — what the visual editor's side panel shows for it. */
    public static List<String> fieldsOf(BlockType type) {
        var own = FIELDS_OF.getOrDefault(type, java.util.Set.of());
        return BY_TYPE_ORDER.stream().filter(own::contains).toList();
    }

    static final List<String> BY_TYPE_ORDER = List.of("html", "itemId", "sectionId", "limit", "links", "mediaId",
            "remoteSourceId", "remoteRoute", "remoteAttributes");
    static final java.util.Set<String> BY_TYPE = java.util.Set.copyOf(BY_TYPE_ORDER);

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        if ("id".equals(fieldName)) {
            return true;
        }
        // the HTML in each language is the type's «html»
        var field = fieldName.startsWith("html") ? "html" : fieldName;
        if (!BY_TYPE.contains(field)) {
            return false;
        }
        return !FIELDS_OF.getOrDefault(type == null ? BlockType.HTML : type, java.util.Set.of()).contains(field);
    }

    @Override
    public String toString() {
        var title = titleEs != null ? titleEs : titleEn != null ? titleEn : titleDe;
        return (type == null ? "" : type.label()) + (title == null ? "" : " · " + title);
    }
}
