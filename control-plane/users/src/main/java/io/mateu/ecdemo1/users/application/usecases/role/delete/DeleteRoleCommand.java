package io.mateu.ecdemo1.users.application.usecases.role.delete;

import java.util.List;

public record DeleteRoleCommand(List<String> ids) {
}
