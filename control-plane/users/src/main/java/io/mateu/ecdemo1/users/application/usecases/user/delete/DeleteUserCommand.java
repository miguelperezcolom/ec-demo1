package io.mateu.ecdemo1.users.application.usecases.user.delete;

import java.util.List;

public record DeleteUserCommand(List<String> ids) {
}
