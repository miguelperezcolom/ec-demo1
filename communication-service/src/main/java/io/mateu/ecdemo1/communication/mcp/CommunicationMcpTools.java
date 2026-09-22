package io.mateu.ecdemo1.communication.mcp;

import io.mateu.ecdemo1.communication.send.Deliveries;
import io.mateu.ecdemo1.communication.store.NotificationRepository;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CommunicationMcpTools implements McpSystemContext {

    final NotificationRepository notifications;
    final RecipientRepository recipients;
    final Deliveries deliveries;

    @Override
    public String getSystemContext() {
        return """
                Notificaciones de la integración:
                - La integración avisa de causas nuevas que bloquean procesos, de propuestas de mapeado que esperan
                  revisión, de escrituras en el PMS que llevan demasiado tiempo reintentándose y de rechazos del PMS.
                - Cada aviso va a los destinatarios configurados para su tipo y su hotel (vacío = cualquiera).
                """;
    }

    public record NotificationView(String id, String requestedAt, String type, String hotel, String title, String status,
                                   String recipients, int attempts, String lastError) {
    }

    public record RecipientView(String id, String name, String email, String type, String hotel, boolean active) {
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

    @Tool(description = "Who receives which notifications, for which hotel")
    public List<RecipientView> listRecipients() {
        return recipients.findAll().stream().map(r -> new RecipientView(r.id, r.name, r.email,
                r.notificationType == null ? "any" : r.notificationType.name(), r.hotelCode == null ? "any" : r.hotelCode, r.active)).toList();
    }

    @Tool(description = "Add a recipient. Leave type or hotel empty for any. Types: CAUSE_OPENED, PROPOSAL_READY, "
            + "RETRYING_TOO_LONG, PMS_REJECTED")
    public String addRecipient(String name, String email, @ToolParam(required = false) NotificationType type,
                               @ToolParam(required = false) String hotelCode) {
        var r = new Recipient();
        r.id = UUID.randomUUID().toString();
        r.name = name;
        r.email = email;
        r.notificationType = type;
        r.hotelCode = hotelCode == null || hotelCode.isBlank() ? null : hotelCode;
        r.active = true;
        recipients.save(r);
        return "Recipient %s added".formatted(r.id);
    }
}
