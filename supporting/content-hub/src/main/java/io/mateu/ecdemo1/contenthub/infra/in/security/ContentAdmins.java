package io.mateu.ecdemo1.contenthub.infra.in.security;

import io.mateu.uidl.annotations.EyesOnly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Only for {@code content:admin}: sections, named audiences, slots, newsletter and the projection. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
@EyesOnly(permissions = "content:admin")
public @interface ContentAdmins {
}
