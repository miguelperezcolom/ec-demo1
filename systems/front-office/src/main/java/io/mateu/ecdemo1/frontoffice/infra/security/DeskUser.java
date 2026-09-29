package io.mateu.ecdemo1.frontoffice.infra.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The person at the desk, as the request's token names them — for the audit trail. */
public final class DeskUser {

  private DeskUser() {
  }

  /** Their username; "recepción" when the request carries no token. */
  public static String name() {
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth instanceof JwtAuthenticationToken jwt) {
        var username = jwt.getToken().getClaimAsString("preferred_username");
        return username != null && !username.isBlank() ? username : jwt.getName();
      }
      if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
        return auth.getName();
      }
    } catch (RuntimeException e) {
      // no security context: the desk
    }
    return "recepción";
  }
}
