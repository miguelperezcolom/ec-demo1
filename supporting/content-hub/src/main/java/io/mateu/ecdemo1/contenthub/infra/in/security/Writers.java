package io.mateu.ecdemo1.contenthub.infra.in.security;

import io.mateu.uidl.annotations.EyesOnly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Only for {@code content:edit} (or {@code content:admin}): what changes a draft — restoring a version. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@EyesOnly(permissions = {"content:edit", "content:admin"})
public @interface Writers {
}
