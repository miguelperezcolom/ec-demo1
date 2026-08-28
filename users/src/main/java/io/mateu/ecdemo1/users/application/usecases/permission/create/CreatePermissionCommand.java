package io.mateu.ecdemo1.users.application.usecases.permission.create;

public record CreatePermissionCommand(String name, String description, String scope) {
}
