package io.mateu.ecdemo1.communication.mcp;

import io.mateu.ecdemo1.communication.application.Recipients;
import io.mateu.ecdemo1.communication.send.Deliveries;
import io.mateu.ecdemo1.communication.send.TestPushes;
import io.mateu.ecdemo1.communication.store.Channel;
import io.mateu.ecdemo1.communication.store.NotificationRepository;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CommunicationMcpTools implements McpSystemContext {

    final NotificationRepository notifications;
    final RecipientRepository recipients;
    final Deliveries deliveries;
    final Recipients recipientChanges;
    final TestPushes testPushes;

    @Override
    public String getSystemContext() {
        return """
                Notificaciones de la integración:
                - La integración avisa de causas nuevas que bloquean procesos, de propuestas de mapeado que esperan
                  revisión, de escrituras en el PMS que llevan demasiado tiempo reintentándose y de rechazos del PMS.
                - La tabla de destinatarios es la única regla de quién se entera de qué y por dónde. Cada destinatario
                  dice a quién (usuarios de Keycloak y/o roles del realm, o una dirección de email), qué (tipos de
                  aviso — ninguno = todos —, si también las tareas del motor de formularios, y un hotel — vacío =
                  todos) y por dónde: INBOX (la bandeja de sus personas), WEB_PUSH (sus navegadores en las consolas),
                  FRONT_DESK_PUSH (sus navegadores en recepción, el front office), EMAIL (su dirección) y
                  GOOGLE_CHAT (los espacios que nombra; vacío = todos).
                - Una persona activa los avisos en cada navegador (menú de usuario → Avisos). sendTestPush le manda
                  una prueba a todos sus navegadores, para ver que llegan.
                - Cada aviso llega a todos los destinatarios activos que lo quieren, una vez por persona, navegador,
                  dirección y espacio. Urgente es lo que alguien pidió recibir por email.
                """;
    }

    public record NotificationView(String id, String requestedAt, String type, String hotel, String title, String status,
                                   String recipients, int attempts, String lastError) {
    }

    public record RecipientView(String id, String name, String users, String roles, String email, String types, boolean tasks,
                                String hotel, String channels, String chatSpaces, boolean active) {
    }

    @Tool(description = "Recent notifications, newest first, with whether they were delivered and to whom")
    public List<NotificationView> listNotifications(@ToolParam(required = false, description = "only this hotel") String hotelCode) {
        return notifications.findAllByOrderByRequestedAtDesc().stream()
                .filter(n -> hotelCode == null || hotelCode.isBlank() || hotelCode.equals(n.hotelCode))
                .limit(50)
                .map(n -> new NotificationView(n.id, String.valueOf(n.requestedAt), String.valueOf(n.type), n.hotelCode, n.title,
                        String.valueOf(n.status), n.recipients, n.attempts, n.lastError))
                .toList();
    }

    @Tool(description = "Send a notification again, to whoever should receive it now")
    public String resendNotification(String notificationId) {
        var n = deliveries.resend(notificationId);
        return "%s: %s to %s".formatted(n.id, n.status, n.recipients);
    }

    @Tool(description = "Who is told about which notifications, for which hotel, and where (inbox, browser, e-mail, Google Chat)")
    public List<RecipientView> listRecipients() {
        return recipients.findAll().stream().map(r -> new RecipientView(r.id, r.name, r.users, r.roles, r.email,
                r.types == null ? "all" : r.types, r.tasks, r.hotelCode == null ? "any" : r.hotelCode,
                Recipient.join(r.channelSet()), r.chatSpaces, r.active)).toList();
    }

    @Tool(description = "Add a recipient: who (users and/or roles, or an e-mail address), what (types, tasks, hotel) and where "
            + "(channels). Types: CAUSE_OPENED, PROPOSAL_READY, RETRYING_TOO_LONG, PMS_REJECTED, INTEGRATION_NEEDS_ATTENTION, CHECK_IN_INCOMPLETE; "
            + "none is all. Channels: INBOX, WEB_PUSH (the consoles' browsers), FRONT_DESK_PUSH (the front office's browsers) — these three need "
            + "users or roles —, EMAIL (needs the address), GOOGLE_CHAT")
    public String addRecipient(String name,
                               @ToolParam(description = "how it is told") List<Channel> channels,
                               @ToolParam(required = false, description = "Keycloak usernames, comma-separated") String users,
                               @ToolParam(required = false, description = "realm roles, comma-separated") String roles,
                               @ToolParam(required = false, description = "where EMAIL goes") String email,
                               @ToolParam(required = false, description = "the types it wants; none is all") List<NotificationType> types,
                               @ToolParam(required = false, description = "whether it also wants the forms engine's tasks") Boolean tasks,
                               @ToolParam(required = false, description = "only this hotel; empty is all") String hotelCode,
                               @ToolParam(required = false, description = "Google Chat spaces by number, e.g. \"1,2\"; empty is all") String chatSpaces) {
        var set = channels == null || channels.isEmpty() ? EnumSet.noneOf(Channel.class) : EnumSet.copyOf(channels);
        var problems = Recipients.problems(set, blank(users), blank(roles), blank(email));
        if (!problems.isEmpty()) {
            return "Not added: " + String.join(" ", problems);
        }
        var id = recipientChanges.save(new Recipients.RecipientChange(null, name, true, blank(users), blank(roles),
                blank(email), types, Boolean.TRUE.equals(tasks), blank(hotelCode), set, blank(chatSpaces)));
        return "Recipient %s added".formatted(id);
    }
    @Tool(description = "Send a test notification to every browser where this Keycloak user enabled notifications — the consoles' "
            + "and the front desk's — to check that Web Push reaches them. Says, per browser, whether the push service took it")
    public List<TestPushes.Result> sendTestPush(@ToolParam(description = "the Keycloak username") String username) {
        return testPushes.toUser(username);
    }

    static String blank(String value) {
        return value == null || value.isBlank() ? null : value.replace(" ", "");
    }
}
