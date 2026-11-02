package io.mateu.ecdemo1.contenthub.infra.in.seed;

import io.mateu.ecdemo1.contenthub.application.usecases.Templates;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.template.PageTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * The demo's page templates, each created if missing (by its id) on every boot — an environment seeded
 * before templates existed gets them too, and one an editor changed is left alone.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateSeeder {

    final Templates templates;
    final Clock clock;

    @Value("${content-hub.seed.enabled:true}")
    boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled) {
            return;
        }
        try {
            demoTemplates(clock).forEach(templates::seed);
        } catch (RuntimeException e) {
            log.error("Could not seed the page templates", e);
        }
    }

    static Block block(String id, BlockType type, String title, String html, Integer limit, List<Block.Link> links) {
        return new Block(id, type, null, title, html, null, null, limit, links, null);
    }

    static List<PageTemplate> demoTemplates(Clock clock) {
        var now = clock.instant();
        return List.of(
                PageTemplate.create("t-home-equipo", "Home de equipo o departamento",
                        "El inicio de un equipo: saludo, lo que tiene pendiente, sus enlaces de trabajo, avisos y noticias. "
                                + "Ponle la audiencia del equipo (departamento, hotel…).",
                        List.of(block("eq-hola", BlockType.GREETING, null, null, null, null),
                                block("eq-banner", BlockType.BANNER, "Equipo de [nombre del equipo]",
                                        "<p>Una frase sobre el equipo: qué hace y para quién.</p>", null, null),
                                block("eq-pendientes", BlockType.PENDING, null, null, null, null),
                                block("eq-enlaces", BlockType.QUICK_LINKS, "Para el día a día", null, null,
                                        List.of(new Block.Link("Procedimientos del equipo", "/intranet/documentos"),
                                                new Block.Link("Personas del equipo", "/intranet/personas"),
                                                new Block.Link("Buscar en la intranet", "/intranet/buscar"))),
                                block("eq-avisos", BlockType.NOTICES, null, null, 5, null),
                                block("eq-noticias", BlockType.LATEST, null, null, 5, null)),
                        null, "demo", now),
                PageTemplate.create("t-bienvenida", "Página de bienvenida",
                        "Para quien llega: un banner de bienvenida, unas palabras, los enlaces imprescindibles y las últimas noticias.",
                        List.of(block("bv-banner", BlockType.BANNER, "¡Bienvenido/a!",
                                        "<p>Nos alegra tenerte aquí. Esta página reúne lo que necesitas en tus primeros días.</p>", null, null),
                                block("bv-texto", BlockType.HTML, "Tus primeros días",
                                        "<p>Escribe aquí qué pasa la primera semana: a quién conocerá, qué formación hará y "
                                                + "a quién preguntar.</p>", null, null),
                                block("bv-enlaces", BlockType.QUICK_LINKS, "Imprescindibles", null, null,
                                        List.of(new Block.Link("Organigrama", "/intranet/organigrama"),
                                                new Block.Link("Pedir material o formación", "/intranet/nuevaSolicitud"),
                                                new Block.Link("Mis preferencias", "/intranet/preferencias"))),
                                block("bv-noticias", BlockType.LATEST, "Últimas noticias", null, 5, null)),
                        null, "demo", now),
                PageTemplate.create("t-campana", "Landing de campaña",
                        "Una campaña interna (seguridad, sostenibilidad, un lanzamiento): banner, qué es y por qué, "
                                + "un vídeo o imagen, los enlaces para participar y las novedades de la campaña.",
                        List.of(block("cp-banner", BlockType.BANNER, "[Nombre de la campaña]",
                                        "<p>El lema de la campaña en una frase.</p>", null, null),
                                block("cp-que", BlockType.HTML, "¿De qué se trata?",
                                        "<p>Qué queremos conseguir, desde cuándo y hasta cuándo, y por qué importa.</p>", null, null),
                                new Block("cp-medio", BlockType.MEDIA, null, "Mira el vídeo", null, null, null, null, null, null),
                                block("cp-participa", BlockType.QUICK_LINKS, "Cómo participar", null, null,
                                        List.of(new Block.Link("Responde a la encuesta", "/intranet/encuestas"),
                                                new Block.Link("Pregunta tus dudas", "/intranet/buscar"))),
                                block("cp-novedades", BlockType.LATEST, "Novedades de la campaña", null, 5, null)),
                        null, "demo", now));
    }
}
