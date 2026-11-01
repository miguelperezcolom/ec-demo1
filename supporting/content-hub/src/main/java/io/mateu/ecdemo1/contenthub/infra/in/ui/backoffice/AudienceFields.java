package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import io.mateu.uidl.data.Option;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The visual audience builder's model: a named audience and, per attribute, the values admitted —
 * "the named audience AND each attribute IN its values", empty meaning everyone. An expression the
 * builder cannot show (an OR at the top, say) is kept as it is until the editor touches the fields.
 */
public final class AudienceFields {

    public static final Map<String, Attribute> FIELDS = Map.of(
            "companies", Attribute.COMPANY, "countries", Attribute.COUNTRY, "regions", Attribute.REGION,
            "hotels", Attribute.HOTEL, "departments", Attribute.DEPARTMENT, "jobTitles", Attribute.JOB_TITLE);

    private AudienceFields() {
    }

    /** @param values the selected values per attribute (null or empty lists are ignored) */
    public static Audience build(String named, Map<Attribute, List<String>> values, String advanced) {
        var parts = new ArrayList<Audience>();
        if (named != null && !named.isBlank()) {
            parts.add(new Audience.Named(named));
        }
        values.forEach((attribute, selected) -> {
            if (selected != null && !selected.isEmpty()) {
                parts.add(new Audience.AttributeIn(attribute, new java.util.TreeSet<>(selected)));
            }
        });
        if (parts.isEmpty()) {
            return advanced == null || advanced.isBlank() ? Audience.EVERYONE : Json.audience(advanced);
        }
        return parts.size() == 1 ? parts.getFirst() : new Audience.AllOf(parts);
    }

    /** What the builder can show of an audience; {@code advanced} holds it whole when it cannot. */
    public record Parsed(String named, Map<Attribute, List<String>> values, String advanced) {
    }

    public static Parsed parse(Audience audience) {
        var values = new EnumMap<Attribute, List<String>>(Attribute.class);
        String named = null;
        var parts = switch (audience) {
            case null -> List.<Audience>of();
            case Audience.Everyone e -> List.<Audience>of();
            case Audience.AllOf all -> all.parts();
            default -> List.of(audience);
        };
        for (var p : parts) {
            if (p instanceof Audience.Named n && named == null) {
                named = n.audienceId();
            } else if (p instanceof Audience.AttributeIn in && !values.containsKey(in.attribute())) {
                values.put(in.attribute(), new ArrayList<>(in.values()));
            } else {
                return new Parsed(null, new EnumMap<>(Attribute.class), Json.write(audience));
            }
        }
        return new Parsed(named, values, null);
    }

    public static boolean supports(String fieldName) {
        return FIELDS.containsKey(fieldName) || fieldName.startsWith("block") && FIELDS.containsKey(uncap(fieldName.substring(5)));
    }

    public static List<Option> options(String fieldName) {
        var attribute = FIELDS.get(fieldName);
        if (attribute == null && fieldName.startsWith("block")) {
            attribute = FIELDS.get(uncap(fieldName.substring(5)));
        }
        return attribute == null ? List.of() : Catalog.get().values(attribute);
    }

    static String uncap(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
