package io.mateu.ecdemo1.contenthub.infra.in.mcp;

import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who is calling an MCP tool: the person whose token came with the call (ia-agent forwards the chatter's, a
 * desktop client sends its user's; the security chain verified it) — the real reader, never "ver como".
 */
@Component
@RequiredArgsConstructor
public class McpCaller {

    final Readers readers;

    public Reader reader() {
        var request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
        return readers.real(h -> request == null ? null : request.getHeader(h));
    }
}
