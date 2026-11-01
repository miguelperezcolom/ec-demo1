package io.mateu.ecdemo1.users.application.usecases.user.save;

import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeProfile;

import java.util.List;

public record SaveUserCommand(String id,
                              String name,
                              String email,
                              List<String> groups,
                              List<String> roles,
                              EmployeeAttributes attributes,
                              EmployeeProfile profile) {

    public SaveUserCommand {
        groups = groups != null ? groups : List.of();
        roles = roles != null ? roles : List.of();
        attributes = attributes != null ? attributes : EmployeeAttributes.NONE;
        profile = profile != null ? profile : EmployeeProfile.NONE;
    }

    public SaveUserCommand(String id, String name, String email, List<String> groups, List<String> roles,
                           EmployeeAttributes attributes) {
        this(id, name, email, groups, roles, attributes, EmployeeProfile.NONE);
    }
}
