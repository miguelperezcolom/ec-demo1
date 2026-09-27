package io.mateu.ecdemo1.users.application.usecases.user.save;

import java.util.List;

public record SaveUserCommand(String id,
                              String name,
                              String email,
                              List<String> groups,
                              List<String> roles) {
}
