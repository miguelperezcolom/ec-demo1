package io.mateu.ecdemo1.frontoffice.infra.persistence;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Marks an HTTP request as one whose reads {@link RequestCache} may share. */
@Component
class RequestCacheFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    request.setAttribute(RequestCache.ENABLED, Boolean.TRUE);
    chain.doFilter(request, response);
  }
}
