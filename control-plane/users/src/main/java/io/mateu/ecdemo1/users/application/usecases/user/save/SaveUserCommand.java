package io.mateu.ecdemo1.users.application.usecases.user.save;

import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;

import java.util.List;

public record SaveUserCommand(String id,
                              String name,
                              String email,
                              List<String> groups,
                              List<String> roles,
                              EmployeeAttributes attributes) {

    public SaveUserCommand {
        groups = groups != null ? groups : List.of();
        roles = roles != null ? roles : List.of();
        attributes = attributes != null ? attributes : EmployeeAttributes.NONE;
    }
}
