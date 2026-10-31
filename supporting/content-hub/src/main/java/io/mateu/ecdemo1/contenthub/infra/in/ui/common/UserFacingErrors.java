package io.mateu.ecdemo1.contenthub.infra.in.ui.common;

import io.mateu.uidl.UserFacingException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * The domain and the use cases say no with plain exceptions — {@code IllegalStateException("No hay
 * cambios que revisar")}, {@code IllegalArgumentException}, {@code SecurityException("Hace falta el
 * permiso …")} — whose messages are written for the person who pressed the button. Mateu shows only a
 * {@link UserFacingException}'s message; anything else becomes "an unexpected error" with a reference.
 * So, at the boundary the screens call through, the use cases' refusals become user-facing, and the
 * application layer stays free of Mateu.
 */
@Aspect
@Component
public class UserFacingErrors {

    @Around("within(io.mateu.ecdemo1.contenthub.application.usecases..*) && execution(public * *(..))")
    public Object refusalsAsMessages(ProceedingJoinPoint call) throws Throwable {
        try {
            return call.proceed();
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw new UserFacingException(e.getMessage(), e);
        }
    }
}
