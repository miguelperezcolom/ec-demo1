package io.mateu.ecdemo1.contenthub.infra.out.ai;

import io.mateu.ecdemo1.contenthub.application.out.AltTextGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * {@link AltTextGenerator} on the IA control plane's {@code intranet-alt-text}, over A2A with the editor's
 * token, as «Traducir con IA» does: one message with the image inline (a file part) and a short request.
 * The agent's model must see images; one that does not answers that it sees none, which is a failure here,
 * never a text. The reply is checked: one short line, or nothing is offered.
 */
@Component
@Slf4j
public class AgentAltText implements AltTextGenerator {

    static final String NOT_AVAILABLE = "La IA no está disponible ahora; no se ha cambiado nada";
    /** What the agent answers when it receives no image (its system prompt says so too). */
    static final String NO_IMAGE = "SIN_IMAGEN";

    final A2aClient client;
    final String url;
    final Duration timeout;

    public AgentAltText(A2aClient client,
                        @Value("${content-hub.alt-text.agent-url:http://ia-agent:8095/a2a/intranet-alt-text}") String url,
                        @Value("${content-hub.alt-text.agent-timeout:PT60S}") Duration timeout) {
        this.client = client;
        this.url = url;
        this.timeout = timeout;
    }

    @Override
    public boolean available() {
        return url != null && !url.isBlank();
    }

    @Override
    public String suggest(byte[] image, String contentType, String context, String authorization) {
        String reply;
        try {
            reply = client.send(url, message(context), List.of(new A2aClient.Image(contentType, image)), authorization,
                    timeout);
        } catch (A2aClient.A2aException e) {
            log.info("Alt text suggestion failed: {}", e.getMessage());
            throw new IllegalStateException(NOT_AVAILABLE);
        }
        return checked(reply);
    }

    /** The request, with the context as data: the image is what it is about. */
    static String message(String context) {
        return "Escribe el texto alternativo (atributo alt) de la imagen adjunta, en español, para la intranet de"
                + " una cadena hotelera: una sola frase, de 125 caracteres como mucho, que diga lo que se ve y lo que"
                + " importa para entender el contenido; sin empezar por «Imagen de» ni «Foto de», sin comillas ni"
                + " comentarios. Si no recibes ninguna imagen, responde exactamente " + NO_IMAGE + ".\n\n"
                + "<contexto>" + AgentAnswers.escape(context) + "</contexto>";
    }

    static String checked(String reply) {
        var text = reply == null ? "" : reply.trim();
        if (text.isEmpty() || text.toUpperCase(Locale.ROOT).contains(NO_IMAGE)) {
            throw new IllegalStateException("La IA no ha podido ver la imagen; escribe el texto a mano");
        }
        var firstLine = text.lines().map(String::trim).filter(l -> !l.isEmpty()).findFirst().orElse("");
        if (firstLine.length() > 400) {
            throw new IllegalStateException("La IA no ha devuelto un texto alternativo válido; no se ha cambiado nada");
        }
        return firstLine;
    }
}
