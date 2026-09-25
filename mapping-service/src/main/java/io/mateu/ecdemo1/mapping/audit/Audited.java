package io.mateu.ecdemo1.mapping.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an action auditable (HLA F016): every call to it — carried out or refused — leaves an
 * AccionAuditada, whichever door it came through (console, REST, an agent's tool). Not everything is
 * audited, only what has consequences on a hotel's operation. By convention the method's last
 * parameter is who acts.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** The kind of action, as the audit trail names it. */
    String value();
}
