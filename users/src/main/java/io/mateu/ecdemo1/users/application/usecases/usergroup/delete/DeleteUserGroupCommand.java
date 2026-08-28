package io.mateu.ecdemo1.users.application.usecases.usergroup.delete;

import java.util.List;

public record DeleteUserGroupCommand(List<String> ids) {
}
