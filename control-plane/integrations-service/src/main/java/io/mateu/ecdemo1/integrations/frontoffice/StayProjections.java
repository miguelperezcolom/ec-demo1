package io.mateu.ecdemo1.integrations.frontoffice;

import io.mateu.ecdemo1.integration.model.process.Definitions;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integrations.outbox.Outbox;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import io.mateu.workflow.security.AuthorizationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Starts «proyectar-estancia» for a PMS reservation: its connector step reads the reservation as the
 * PMS holds it and writes it into the front office. Through the outbox, in the transaction of the
 * decision that asks for it. The engine ignores a business key it already has — that is the dedup:
 * the key names the reservation and the version of it being projected (the PMS's last modification,
 * or the event that announced it), so the backfill, the polling and the connector's own event asking
 * for the same version start one process.
 */
@Component
@RequiredArgsConstructor
public class StayProjections {

    final Outbox outbox;

    @Transactional(propagation = Propagation.MANDATORY)
    public String project(String integrationId, String pmsHotelCode, String pmsReservationId, String version, String origin) {
        var key = businessKey(pmsHotelCode, pmsReservationId, version);
        outbox.appendToEngine(new ProcessCreationRequested(Definitions.PROJECT_STAY, key, List.of(
                new Variable(ProcessVariables.PROCESS_KEY, key),
                new Variable(ProcessVariables.DEFINITION_ID, Definitions.PROJECT_STAY),
                new Variable(ProcessVariables.PMS_HOTEL_CODE, pmsHotelCode),
                new Variable(ProcessVariables.PMS_RESERVATION_ID, pmsReservationId),
                new Variable(ProcessVariables.ORIGIN, origin),
                new Variable(ProcessVariables.INTEGRATION_ID, integrationId)), null, AuthorizationContext.SYSTEM));
        return key;
    }

    public static String businessKey(String pmsHotelCode, String pmsReservationId, String version) {
        return Definitions.PROJECT_STAY + ":" + pmsHotelCode + ":" + pmsReservationId + ":" + version;
    }
}
