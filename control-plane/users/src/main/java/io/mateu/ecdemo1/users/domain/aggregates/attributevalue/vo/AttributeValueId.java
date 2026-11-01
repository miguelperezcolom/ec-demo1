package io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo;

/**
 * An entry of the catalogue, named by its attribute and its code ({@code HOTEL:MRU01}). The code
 * alone is not unique — nothing stops a department and a country from both being "IT" — but the
 * pair is, and it is what a user's attribute refers to once the attribute is known.
 */
public record AttributeValueId(String id) {

    public static AttributeValueId of(Attribute attribute, String code) {
        return new AttributeValueId(attribute.name() + ":" + code);
    }
}
