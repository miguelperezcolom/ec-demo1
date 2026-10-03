package io.mateu.ecdemo1.integrations.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.integrations.outbox.Outbox;
import io.mateu.ecdemo1.integrations.ui.demo.DemoBannerProbe;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResetNoticeTest {

    @Test
    void oneNoticeAndOneAuditRecordPerRunWhateverTheRetries() {
        var outbox = mock(Outbox.class);
        var notice = new ResetNotice(outbox, Clock.fixed(Instant.parse("2026-10-03T17:00:00Z"), ZoneOffset.UTC));
        var result = new ResetNotice.Result("reset-demo:1", "Ana", "admin", "ECDEMO1-10031700", "2 Case(s), 5 contacto(s)",
                "7", "B1,B2", true, "OK   booking", "https://ec1/workflow/processes/p1");

        notice.notify(result);
        notice.notify(result);

        var notifications = ArgumentCaptor.forClass(NotificationRequested.class);
        verify(outbox, times(2)).appendNotification(notifications.capture());
        var n = notifications.getValue();
        assertThat(n.type()).isEqualTo(NotificationType.PLATFORM_ALERT);
        assertThat(n.title()).isEqualTo("La demo está a cero");
        assertThat(n.body()).contains("lanzado por Ana", "confirmado por admin", "ECDEMO1-10031700", "2 Case(s)", "B1,B2");
        assertThat(n.link()).isEqualTo("https://ec1/workflow/processes/p1");
        assertThat(notifications.getAllValues().get(0)).isEqualTo(notifications.getAllValues().get(1));

        var audits = ArgumentCaptor.forClass(AuditedAction.class);
        verify(outbox, times(2)).appendAudit(audits.capture());
        assertThat(audits.getValue().action()).isEqualTo("Demo: reset completado");
        assertThat(audits.getValue().by()).isEqualTo("Ana");
        assertThat(audits.getAllValues().get(0).actionId()).isEqualTo(audits.getAllValues().get(1).actionId());
    }

    @Test
    void theBannerSaysItOnlyWhileActive() {
        assertThat(DemoBannerProbe.html("17:15")).contains("Simulación: Opera no responde (hasta las 17:15)");
    }
}
