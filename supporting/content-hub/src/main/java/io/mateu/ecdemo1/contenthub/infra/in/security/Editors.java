package io.mateu.ecdemo1.contenthub.infra.in.security;

import io.mateu.uidl.annotations.EyesOnly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Only for whoever holds an editorial permission ({@code content:edit}, {@code content:publish} or
 * {@code content:admin}) — the backoffice's screens and its menu entry. Mateu hides the entry and refuses
 * the route to anyone else, typed URL included; the identity comes from {@link EditorialIdentity}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
@EyesOnly(permissions = {"content:edit", "content:publish", "content:admin"})
public @interface Editors {
}
