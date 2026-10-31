package io.mateu.ecdemo1.contenthub.infra.in.security;

import io.mateu.uidl.annotations.EyesOnly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Only for {@code content:publish} (or {@code content:admin}): what is seen as soon as it is saved — slot variants. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@EyesOnly(permissions = {"content:publish", "content:admin"})
public @interface Publishers {
}
