package io.mateu.ecdemo1.contenthub.infra.out.ai;

import io.mateu.ecdemo1.contenthub.application.out.AnswerGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * {@link AnswerGenerator} on the IA control plane's {@code intranet-agent}. It has no tools: all it knows is
 * the message, and content-hub has already reduced that to the passages the reader may read.
 */
@Component
public class AgentAnswers implements AnswerGenerator {

    final A2aClient client;
    final String url;
    final Duration timeout;

    public AgentAnswers(A2aClient client,
                        @Value("${content-hub.search.agent-url:http://ia-agent:8095/a2a/intranet-agent}") String url,
                        @Value("${content-hub.search.agent-timeout:PT30S}") Duration timeout) {
        this.client = client;
        this.url = url;
        this.timeout = timeout;
    }

    @Override
    public boolean available() {
        return url != null && !url.isBlank();
    }

    @Override
    public String answer(String question, List<Source> sources, String authorization) {
        return client.send(url, message(question, sources), authorization, timeout);
    }

    /** The passages, numbered, and the question. Their text cannot close its tag: it is data to the agent. */
    static String message(String question, List<Source> sources) {
        var sb = new StringBuilder("<passages>\n");
        for (int i = 0; i < sources.size(); i++) {
            var s = sources.get(i);
            sb.append("<passage number=\"").append(i + 1).append("\" item=\"").append(escape(s.itemId()))
                    .append("\" title=\"").append(escape(s.title())).append("\">\n")
                    .append(escape(s.text())).append("\n</passage>\n");
        }
        return sb.append("</passages>\n\n<question>").append(escape(question)).append("</question>").toString();
    }

    static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
