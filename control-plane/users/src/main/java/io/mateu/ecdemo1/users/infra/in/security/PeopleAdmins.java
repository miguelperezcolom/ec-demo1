package io.mateu.ecdemo1.users.infra.in.security;

import io.mateu.uidl.annotations.EyesOnly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Only for whoever keeps the employees ({@code people:edit}): Mateu hides the entry and refuses the route, typed URL included. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
@EyesOnly(permissions = {"people:edit"})
public @interface PeopleAdmins {
}
