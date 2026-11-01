package io.mateu.ecdemo1.users.application.usecases.user.create;

import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeProfile;

import java.util.List;

/**
 * @param attributes             the employee attributes; country and region are derived from the hotel
 *                               when there is one
 * @param profile                manager and work phone, for the intranet directory
 * @param skipPasswordSetupEmail create the Keycloak user without the set-password email: for seeded
 *                               users at example.com, whose mail nobody reads
 */
public record CreateUserCommand(String id, String name, String email, List<String> groupIds, List<String> roleIds,
                                EmployeeAttributes attributes, EmployeeProfile profile,
                                boolean skipPasswordSetupEmail) {

    public CreateUserCommand {
        groupIds = groupIds != null ? groupIds : List.of();
        roleIds = roleIds != null ? roleIds : List.of();
        attributes = attributes != null ? attributes : EmployeeAttributes.NONE;
        profile = profile != null ? profile : EmployeeProfile.NONE;
    }

    public CreateUserCommand(String id, String name, String email, List<String> groupIds, List<String> roleIds,
                             EmployeeAttributes attributes, boolean skipPasswordSetupEmail) {
        this(id, name, email, groupIds, roleIds, attributes, EmployeeProfile.NONE, skipPasswordSetupEmail);
    }

    public CreateUserCommand(String id, String name, String email, List<String> groupIds, List<String> roleIds,
                             EmployeeAttributes attributes) {
        this(id, name, email, groupIds, roleIds, attributes, EmployeeProfile.NONE, false);
    }
}
