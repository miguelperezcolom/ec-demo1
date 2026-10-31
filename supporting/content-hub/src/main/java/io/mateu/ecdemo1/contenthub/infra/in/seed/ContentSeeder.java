package io.mateu.ecdemo1.contenthub.infra.in.seed;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NavigationCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService;
import io.mateu.ecdemo1.contenthub.application.usecases.SlotCommands;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.domain.slot.SlotVariant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The demo's content, once, into an empty service: sections, named audiences, items for different
 * audiences — one scheduled, one expired, one to confirm —, a page with blocks, a video, a slot with
 * variants and a newsletter edition. Enough for "ver como" to show different profiles different things.
 * Uses the hotels, departments and job titles the users service seeds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentSeeder {

    static final Reader SYSTEM = new Reader("demo", "Demo", EmployeeAttributes.NONE,
            EnumSet.allOf(EditorialPermission.class), null);

    final ItemRepository items;
    final ItemCommands itemCommands;
    final NavigationCommands navigation;
    final NavigationRepository navigationRepository;
    final AudienceCommands audiences;
    final NamedAudienceRepository namedAudiences;
    final SlotCommands slots;
    final SlotRepository slotRepository;
    final MediaRepository media;
    final NewsletterService newsletter;
    final Clock clock;

    @Value("${content-hub.seed.enabled:true}")
    boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!enabled || !items.findAll().isEmpty()) {
            return;
        }
        try {
            run();
            log.info("Demo content seeded");
        } catch (RuntimeException e) {
            log.error("Could not seed the demo content", e);
        }
    }

    static Map<String, Translation> es(String title, String summary, String body) {
        var m = new LinkedHashMap<String, Translation>();
        m.put("es", new Translation(title, summary, body));
        return m;
    }

    static Map<String, Translation> esEn(String title, String summary, String body, String enTitle, String enSummary,
                                         String enBody) {
        var m = es(title, summary, body);
        m.put("en", new Translation(enTitle, enSummary, enBody));
        return m;
    }

    void run() {
        var now = clock.instant();
        // Sections
        if (navigationRepository.get().sections().isEmpty()) {
            navigation.add(SYSTEM, "empresa", null, Map.of("es", "La empresa", "en", "The company"));
            navigation.add(SYSTEM, "noticias", "empresa", Map.of("es", "Noticias", "en", "News"));
            navigation.add(SYSTEM, "personas", null, Map.of("es", "Personas", "en", "People"));
            navigation.add(SYSTEM, "rrhh", "personas", Map.of("es", "RRHH y nóminas", "en", "HR and payroll"));
            navigation.add(SYSTEM, "formacion", "personas", Map.of("es", "Formación", "en", "Training"));
            navigation.add(SYSTEM, "operaciones", null, Map.of("es", "Operaciones", "en", "Operations"));
            navigation.add(SYSTEM, "recepcion", "operaciones", Map.of("es", "Recepción", "en", "Front desk"));
            navigation.add(SYSTEM, "cocina", "operaciones", Map.of("es", "Cocina", "en", "Kitchen"));
            navigation.add(SYSTEM, "direccion", null, Map.of("es", "Dirección", "en", "Management"));
        }
        // Named audiences
        if (namedAudiences.findAll().isEmpty()) {
            audiences.define(SYSTEM, "recepcion-canarias", "Recepción Canarias", Audience.allOf(
                    Audience.in(Attribute.DEPARTMENT, "RECEPCION"), Audience.in(Attribute.REGION, "CANARIAS")));
            audiences.define(SYSTEM, "direccion-hotel", "Dirección de hotel", Audience.in(Attribute.JOB_TITLE, "DIRECTOR_HOTEL"));
            audiences.define(SYSTEM, "espana", "Hoteles de España", Audience.allOf(
                    Audience.in(Attribute.COUNTRY, "ES"), Audience.in(Attribute.COMPANY, "RIUDEMO_HOTELES")));
            audiences.define(SYSTEM, "cocinas", "Equipos de cocina", Audience.in(Attribute.DEPARTMENT, "COCINA"));
        }
        // Media
        media.save(MediaAsset.external("mvideo0001", "Bienvenida del CEO (vídeo)", "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
                "demo", now), null);

        publish("bienvenida", ItemType.ARTICLE, "noticias", Audience.EVERYONE, esEn(
                "Bienvenidos a la nueva intranet",
                "Un solo sitio para las noticias, los avisos y los documentos de toda la cadena.",
                "<p>Desde hoy, todo lo que necesitas saber está aquí, <b>y solo lo que te afecta</b>: cada empleado ve "
                        + "los contenidos de su empresa, su hotel, su departamento y su puesto.</p>"
                        + "<p>Mira el mensaje de bienvenida:</p>[[media:mvideo0001]]",
                "Welcome to the new intranet", "One place for the whole chain's news, notices and documents.",
                "<p>From today everything you need to know is here — and only what concerns you.</p>[[media:mvideo0001]]"),
                null, null, false);
        publish("turnos-canarias", ItemType.NOTICE, "recepcion", new Audience.Named("recepcion-canarias"), es(
                "Nuevo cuadrante de turnos de recepción en Canarias",
                "Los turnos de noviembre cambian en Tenerife y Gran Canaria.",
                "<p>El cuadrante de noviembre ya está disponible. Los turnos de noche pasan a ser de 22:00 a 7:00.</p>"),
                null, null, false);
        publish("menu-otono", ItemType.ARTICLE, "cocina", new Audience.Named("cocinas"), esEn(
                "Carta de otoño: fichas técnicas",
                "Las nuevas fichas técnicas de la carta de otoño, con alérgenos.",
                "<p>Las fichas técnicas incluyen escandallo, alérgenos y emplatado. Entra en vigor el día 15.</p>",
                "Autumn menu: recipe sheets", "The new autumn menu recipe sheets, with allergens.",
                "<p>The sheets include costing, allergens and plating. In force from the 15th.</p>"),
                null, null, false);
        publish("politica-datos", ItemType.NOTICE, "rrhh", Audience.EVERYONE, es(
                "Política de protección de datos 2026",
                "Todos los empleados deben leerla y confirmarlo antes de fin de mes.",
                "<p>Hemos actualizado la política de protección de datos. Resume qué datos de clientes puedes "
                        + "consultar, durante cuánto tiempo se guardan y cómo comunicar una brecha.</p>"),
                null, null, true);
        publish("objetivos-direccion", ItemType.ARTICLE, "direccion", new Audience.Named("direccion-hotel"), es(
                "Objetivos de ocupación del cuarto trimestre",
                "Para directores de hotel: objetivos de ocupación, ADR y satisfacción.",
                "<p>Los objetivos del cuarto trimestre se revisarán en la reunión mensual de dirección.</p>"),
                null, null, false);
        publish("calendario-espana", ItemType.DOCUMENT, "rrhh", new Audience.Named("espana"), es(
                "Calendario laboral 2027 (España)", "Festivos nacionales y autonómicos por hotel.", ""),
                null, null, false, "https://www.boe.es/");
        publish("formacion-idiomas", ItemType.ARTICLE, "formacion", Audience.in(Attribute.COUNTRY, "MU", "MX"), esEn(
                "Cursos de idiomas para hoteles internacionales", "Inglés y francés, en horario de trabajo.",
                "<p>Abiertas las inscripciones para los cursos de idiomas del primer semestre.</p>",
                "Language courses for international hotels", "English and French, during working hours.",
                "<p>Registration is open for the first semester's language courses.</p>"),
                null, null, false);
        publish("gala-navidad", ItemType.ARTICLE, "noticias", Audience.EVERYONE, es(
                "Gala de Navidad 2026", "Se anunciará el 1 de diciembre.", "<p>¡Reserva la fecha!</p>"),
                now.plus(Duration.ofDays(30)), null, false);
        publish("campana-verano", ItemType.NOTICE, "noticias", Audience.EVERYONE, es(
                "Campaña de verano: últimos días", "Este aviso ya ha caducado.", "<p>Caducado.</p>"),
                now.minus(Duration.ofDays(60)), now.minus(Duration.ofDays(1)), false);
        // A page with blocks: one for everyone, one only for the front desk
        var blocks = List.of(
                new Block("b-banner", BlockType.BANNER, null, "Esta semana en la cadena", "<p>Ocupación media 87 % · 4,6 ★</p>",
                        null, null, null, null, null),
                new Block("b-recepcion", BlockType.HTML, Audience.in(Attribute.DEPARTMENT, "RECEPCION"), "Solo para recepción",
                        "<p>Recordad pedir el documento de identidad a todos los huéspedes adultos.</p>", null, null, null, null, null),
                new Block("b-destacado", BlockType.FEATURED, null, "Destacado", null, "politica-datos", null, null, null, null),
                new Block("b-ultimas", BlockType.AUTOMATIC_LISTING, null, "Últimas noticias", null, null, "noticias", 5, null, null),
                new Block("b-enlaces", BlockType.QUICK_LINKS, null, "Enlaces rápidos", null, null, null, null,
                        List.of(new Block.Link("Portal del empleado", "https://example.com/portal"),
                                new Block.Link("Buscar en la intranet", "/intranet/buscar")), null));
        publishPage("portada-operaciones", "operaciones", es("Portada de operaciones",
                "Lo esencial de la semana para los equipos de hotel.", ""), blocks);
        // A slot of the front office, with variants
        if (slotRepository.find("front-office.bienvenida.destacado").isEmpty()) {
            slots.registerDescriptor("front-office", List.of(new SlotCommands.SlotDescriptor(
                    "front-office.bienvenida.destacado", "Destacado en la bienvenida del front office", "Sin novedades")));
            slots.putVariant(SYSTEM, "front-office.bienvenida.destacado", new SlotVariant("general", Audience.EVERYONE, 1,
                    "Nueva intranet", "<p>Consulta las novedades en la intranet.</p>", "/intranet/inicio"));
            slots.putVariant(SYSTEM, "front-office.bienvenida.destacado", new SlotVariant("canarias",
                    new Audience.Named("recepcion-canarias"), 10, "Turnos de noviembre",
                    "<p>El nuevo cuadrante de turnos ya está publicado.</p>", "/intranet/noticias/turnos-canarias"));
        }
        newsletter.compose(SYSTEM, "Boletín semanal", "Lo más importante de la semana.",
                List.of("bienvenida", "turnos-canarias", "menu-otono", "politica-datos"), Audience.EVERYONE);
    }

    void publish(String id, ItemType type, String section, Audience audience, Map<String, Translation> t,
                 java.time.Instant publishAt, java.time.Instant expireAt, boolean confirm) {
        publish(id, type, section, audience, t, publishAt, expireAt, confirm, null);
    }

    void publish(String id, ItemType type, String section, Audience audience, Map<String, Translation> t,
                 java.time.Instant publishAt, java.time.Instant expireAt, boolean confirm, String url) {
        if (items.find(id).isPresent()) {
            return;
        }
        itemCommands.createWithId(id, "editora", type, "es", new ItemContent(section, audience, t, List.of(),
                publishAt, expireAt, confirm, confirm, confirm ? clock.instant().plus(Duration.ofDays(20)) : null, url));
        itemCommands.publish(SYSTEM, id, false);
    }

    void publishPage(String id, String section, Map<String, Translation> t, List<Block> blocks) {
        if (items.find(id).isPresent()) {
            return;
        }
        itemCommands.createWithId(id, "editora", ItemType.PAGE, "es", new ItemContent(section, Audience.EVERYONE, t, blocks,
                null, null, false, false, null, null));
        itemCommands.publish(SYSTEM, id, false);
    }
}
