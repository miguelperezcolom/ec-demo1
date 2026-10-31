package io.mateu.ecdemo1.users.application.usecases.attributevalue.save;

public record SaveAttributeValueCommand(String id, String label, String country, String region) {
}
