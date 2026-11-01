package io.mateu.ecdemo1.intranetshell.infra.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The bare host lands on the portal's home. The home route (/intranet/inicio) belongs to the remote,
 * not to this shell, and a shell loaded at "/" resolves its home among its own routes — "page not
 * found" — while the same route loaded as a deep link resolves through the remote menu. So the
 * browser is sent there before the shell loads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HomeRedirect extends OncePerRequestFilter {

    static final String HOME = "/intranet/inicio";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("GET".equals(request.getMethod()) && "/".equals(request.getRequestURI())) {
            // Relative: sendRedirect would make it absolute with the host this pod was called by —
            // the gateway's upstream (http://intranet-shell), not the browser's.
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", HOME);
            return;
        }
        chain.doFilter(request, response);
    }
}
