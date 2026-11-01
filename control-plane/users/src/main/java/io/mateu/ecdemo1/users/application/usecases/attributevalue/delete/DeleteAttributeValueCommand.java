package io.mateu.ecdemo1.users.application.usecases.attributevalue.delete;

import java.util.List;

public record DeleteAttributeValueCommand(List<String> ids) {
}
