package io.mateu.ecdemo1.users.infra.in.ui;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.ecdemo1.users.infra.in.ui.pages.attributevalues.AttributeValuesCrudOrchestrator;
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

    // Not `users`: a leaf named like its parent menu (UsersHome.users) gets the parent's path from
    // Mateu (/users instead of /users/users), and the CRUD is never reached. Hence the field name, and
    // the label that keeps what the menu showed.
    @Menu
    @Label("Users")
    UsersCrudOrchestrator accounts;

    @Menu
    @Label("Valores de atributo")
    AttributeValuesCrudOrchestrator attributeValues;
}
