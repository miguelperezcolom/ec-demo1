package io.mateu.ecdemo1.users.domain.aggregates.attributevalue;

import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;
import io.mateu.workflow.ddd.AggregateRoot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One value an employee attribute may take: a code, which is what is stored on users and what
 * audiences are written with, and a label, which is what people read. The code never changes once
 * created — users and audiences elsewhere hold it — so only the label (and a hotel's location) is
 * editable.
 *
 * <p>A hotel also says where it is: its country and region codes. That is how an employee's country
 * and region are derived from their hotel instead of being typed, and so cannot disagree with it.
 * For the other attributes the two are empty.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class AttributeValue extends AggregateRoot {

    AttributeValueId id;

    Attribute attribute;

    String code;

    String label;

    String country;

    String region;

    public static AttributeValue of(Attribute attribute, String code, String label, String country, String region) {
        if (attribute == null) throw new IllegalArgumentException("attribute is required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label is required");
        var trimmed = code.trim();
        var value = new AttributeValue(AttributeValueId.of(attribute, trimmed), attribute, trimmed, label, null, null);
        value.locate(country, region);
        return value;
    }

    public void update(String label, String country, String region) {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label is required");
        this.label = label;
        locate(country, region);
    }

    /** Only a hotel has a location; on any other attribute it is dropped rather than kept unread. */
    private void locate(String country, String region) {
        if (attribute == Attribute.HOTEL) {
            this.country = country == null || country.isBlank() ? null : country.trim();
            this.region = region == null || region.isBlank() ? null : region.trim();
        } else {
            this.country = null;
            this.region = null;
        }
    }
}
