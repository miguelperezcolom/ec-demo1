package io.mateu.ecdemo1.users.application.usecases.role.create;

import java.util.List;

public record CreateRoleCommand(String id, String name, String description, List<String> permissionIds) {

    public CreateRoleCommand {
        if (permissionIds == null) permissionIds = List.of();
    }
}
