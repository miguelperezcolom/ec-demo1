package io.mateu.ecdemo1.frontoffice.infra.mcp;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The caller as the servlet request of the tool call says: Spring AI runs a servlet MCP server's tools
 * on the request's own thread (immediate execution), so the token the resource server validated and the
 * {@code sessionId} of the message endpoint are at hand.
 */
@Component
class ServletMcpCaller implements McpCaller {

  @Override
  public String person() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwt) {
      var username = jwt.getToken().getClaimAsString("preferred_username");
      return username != null && !username.isBlank() ? username : jwt.getName();
    }
    return null;
  }

  @Override
  public String turn() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
        ? attributes.getRequest().getParameter("sessionId")
        : null;
  }
}
