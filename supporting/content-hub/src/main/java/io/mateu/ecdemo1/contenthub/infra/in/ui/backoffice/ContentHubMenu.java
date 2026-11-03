package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Publishers;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/**
 * The editorial backoffice, the "Editorial" section of the data plane's shells (see EditorialHome), for whoever
 * holds a content:* permission: the day-to-day work of writing, publishing and following up. How the
 * intranet is configured — sections, audiences, slots, the directory, the projection — is on the control
 * console (ContentHubHome). Each screen also carries its own restriction, so a typed URL is refused like a
 * hidden entry.
 */
public class ContentHubMenu {

    @Menu
    @Label("Contenidos")
    ItemsCrud contenidos;

    @Menu
    @Label("Editor visual")
    VisualEditorListing visual;

    @Menu
    @Label("Calendario")
    EditorialCalendarPage calendario;

    @Menu
    @Label("Historial")
    HistoryListing historial;

    @Menu
    @Label("Nueva desde plantilla")
    NewPageFromTemplatePage nuevaPagina;

    @Menu
    @Label("Plantillas")
    @Publishers
    TemplatesCrud plantillas;

    @Menu
    @Label("Vistas previas")
    PreviewLinksListing vistaPrevia;

    @Menu
    @Label("Medios")
    MediaCrud medios;

    @Menu
    @Label("Slots")
    @Publishers
    SlotContentCrud slots;

    @Menu
    @Label("Newsletters")
    NewsletterCrud newsletters;

    @Menu
    @Label("Números")
    NewsletterIssuesCrud numeros;

    @Menu
    @Label("Editor visual de números")
    IssueVisualEditorListing editorNumeros;

    @Menu
    @Label("Encuestas")
    SurveysCrud encuestas;

    @Menu
    @Label("Respuestas")
    SurveyAnswersListing respuestas;

    @Menu
    @Label("Comentarios")
    CommentsListing comentarios;

    @Menu
    @Label("Ver como")
    ViewAsPage verComo;

    @Menu
    @Label("Confirmaciones")
    ConfirmationsListing confirmaciones;

    @Menu
    @Label("Pendientes")
    PendingConfirmationsListing pendientes;

    @Menu
    @Label("Analítica")
    AnalyticsPage analitica;

    @Menu
    @Label("Salud")
    HealthListing salud;
}
