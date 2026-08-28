package io.mateu.ecdemo1.users.application.usecases.permission.save;

public record SavePermissionCommand(String id, String name, String description, String scope) {
}
