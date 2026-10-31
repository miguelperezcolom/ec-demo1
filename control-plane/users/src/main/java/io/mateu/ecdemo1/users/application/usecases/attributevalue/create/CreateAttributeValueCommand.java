package io.mateu.ecdemo1.users.application.usecases.attributevalue.create;

/** {@code attribute} is an {@code Attribute} constant's name; country and region count only for a hotel. */
public record CreateAttributeValueCommand(String attribute, String code, String label, String country,
                                          String region) {
}
