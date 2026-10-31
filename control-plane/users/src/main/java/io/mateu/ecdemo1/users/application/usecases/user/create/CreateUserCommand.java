package io.mateu.ecdemo1.users.application.usecases.user.create;

import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;

import java.util.List;

/**
 * @param attributes             the employee attributes; country and region are derived from the hotel
 *                               when there is one
 * @param skipPasswordSetupEmail create the Keycloak user without the set-password email: for seeded
 *                               users at example.com, whose mail nobody reads
 */
public record CreateUserCommand(String id, String name, String email, List<String> groupIds, List<String> roleIds,
                                EmployeeAttributes attributes, boolean skipPasswordSetupEmail) {

    public CreateUserCommand {
        groupIds = groupIds != null ? groupIds : List.of();
        roleIds = roleIds != null ? roleIds : List.of();
        attributes = attributes != null ? attributes : EmployeeAttributes.NONE;
    }

    public CreateUserCommand(String id, String name, String email, List<String> groupIds, List<String> roleIds,
                             EmployeeAttributes attributes) {
        this(id, name, email, groupIds, roleIds, attributes, false);
    }
}
