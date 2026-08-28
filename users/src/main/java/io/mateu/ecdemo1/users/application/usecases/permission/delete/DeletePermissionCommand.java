package io.mateu.ecdemo1.users.application.usecases.permission.delete;

import java.util.List;

public record DeletePermissionCommand(List<String> ids) {
}
