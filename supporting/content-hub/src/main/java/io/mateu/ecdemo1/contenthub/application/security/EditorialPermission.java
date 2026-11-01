package io.mateu.ecdemo1.contenthub.application.security;

/** The editorial roles' permissions, as scopes of the users service. */
public enum EditorialPermission {
    /** Create and edit items, send them to review. */
    EDIT("content:edit"),
    /** Approve and publish, retire. */
    PUBLISH("content:publish"),
    /** Sections, named audiences, slots, newsletter. */
    ADMIN("content:admin");

    private final String scope;

    EditorialPermission(String scope) {
        this.scope = scope;
    }

    public String scope() {
        return scope;
    }
}
