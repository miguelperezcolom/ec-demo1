package io.mateu.ecdemo1.users.infra.in.ui;

import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.ecdemo1.users.infra.in.ui.pages.permissions.PermissionsCrudOrchestrator;
import io.mateu.ecdemo1.users.infra.in.ui.pages.roles.RolesCrudOrchestrator;
import io.mateu.ecdemo1.users.infra.in.ui.pages.usergroups.UserGroupCrudOrchestrator;
import io.mateu.ecdemo1.users.infra.in.ui.pages.users.UsersCrudOrchestrator;

public class UsersMenu {

    @Menu
    PermissionsCrudOrchestrator permissions;

    @Menu
    RolesCrudOrchestrator roles;

    @Menu
    UserGroupCrudOrchestrator userGroups;

    @Menu
    UsersCrudOrchestrator users;
}
