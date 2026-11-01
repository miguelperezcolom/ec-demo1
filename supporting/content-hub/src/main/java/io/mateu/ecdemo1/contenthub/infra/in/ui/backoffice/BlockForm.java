package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import io.mateu.uidl.interfaces.VisibilitySupplier;

import java.util.Arrays;
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
    @Label("Título")
    public String title;
    @Label("Contenido (incrusta medios con [[media:id]])")
    @Stereotype(FieldStereotype.richText)
    public String html;
    @Label("Item destacado")
    @Stereotype(FieldStereotype.select)
    public String itemId;
    @Label("Sección listada (vacío: todas)")
    @Stereotype(FieldStereotype.select)
    public String sectionId;
    @Label("Cuántos")
    public Integer limit;
    @Label("Enlaces (una línea por enlace: Texto | URL)")
    @Stereotype(FieldStereotype.textarea)
    public String links;
    @Label("Medio de la biblioteca")
    @Stereotype(FieldStereotype.select)
    public String mediaId;
    @Label("Solo estos departamentos")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> blockDepartments;
    @Label("Solo estos hoteles")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> blockHotels;
    @Label("Solo estos puestos")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> blockJobTitles;
    @Label("Id")
    public String id;

    public static BlockForm of(Block b) {
        var f = new BlockForm();
        f.id = b.id();
        f.type = b.type();
        f.title = b.title();
        f.html = b.html();
        f.itemId = b.itemId();
        f.sectionId = b.sectionId();
        f.limit = b.limit();
        f.mediaId = b.mediaId();
        f.links = String.join("\n", b.links().stream().map(l -> l.label() + " | " + l.url()).toList());
        var parsed = AudienceFields.parse(b.audience());
        f.blockDepartments = parsed.values().get(Attribute.DEPARTMENT);
        f.blockHotels = parsed.values().get(Attribute.HOTEL);
        f.blockJobTitles = parsed.values().get(Attribute.JOB_TITLE);
        return f;
    }

    public Block toBlock() {
        var values = new EnumMap<Attribute, List<String>>(Attribute.class);
        values.put(Attribute.DEPARTMENT, blockDepartments);
        values.put(Attribute.HOTEL, blockHotels);
        values.put(Attribute.JOB_TITLE, blockJobTitles);
        Audience audience = AudienceFields.build(null, values, null);
        var parsedLinks = links == null ? List.<Block.Link>of() : Arrays.stream(links.split("\n"))
                .map(String::trim).filter(l -> !l.isEmpty())
                .map(l -> {
                    var at = l.lastIndexOf('|');
                    return at < 0 ? new Block.Link(l, l) : new Block.Link(l.substring(0, at).trim(), l.substring(at + 1).trim());
                }).toList();
        return new Block(id == null || id.isBlank() ? "b" + UUID.randomUUID().toString().substring(0, 6) : id,
                type == null ? BlockType.HTML : type, audience, title, html, blank(itemId), blank(sectionId), limit,
                parsedLinks, blank(mediaId));
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "itemId".equals(fieldName) || "sectionId".equals(fieldName) || "mediaId".equals(fieldName)
                || AudienceFields.supports(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "itemId" -> Catalog.get().publishedItems();
            case "sectionId" -> Catalog.get().sections();
            case "mediaId" -> Catalog.get().media();
            default -> AudienceFields.options(fieldName);
        };
    }

    /** The fields each type uses (what PageRenderer reads); the title and the audience, always. */
    static final java.util.Map<BlockType, java.util.Set<String>> FIELDS_OF = java.util.Map.of(
            BlockType.HTML, java.util.Set.of("html"),
            BlockType.BANNER, java.util.Set.of("html"),
            BlockType.FEATURED, java.util.Set.of("itemId"),
            BlockType.AUTOMATIC_LISTING, java.util.Set.of("sectionId", "limit"),
            BlockType.QUICK_LINKS, java.util.Set.of("links"),
            BlockType.MEDIA, java.util.Set.of("mediaId"));

    static final java.util.Set<String> BY_TYPE = java.util.Set.of("html", "itemId", "sectionId", "limit", "links", "mediaId");

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        if ("id".equals(fieldName)) {
            return true;
        }
        if (!BY_TYPE.contains(fieldName)) {
            return false;
        }
        return !FIELDS_OF.getOrDefault(type == null ? BlockType.HTML : type, java.util.Set.of()).contains(fieldName);
    }

    @Override
    public String toString() {
        return (type == null ? "" : type.label()) + (title == null ? "" : " · " + title);
    }
}
