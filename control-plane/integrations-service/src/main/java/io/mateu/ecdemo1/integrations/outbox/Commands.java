package io.mateu.ecdemo1.integrations.outbox;

import io.mateu.ecdemo1.integration.model.command.MappingCommand;
import io.mateu.ecdemo1.integration.model.command.ProjectReservation;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.partner.PartnerType;
import io.mateu.ecdemo1.integrations.clients.PartnerCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The commands this service sends other services, in the integration's words: each written to the
 * {@link Outbox} in the transaction of the decision that asks for it, and published once it commits.
 * None is sent over HTTP, and none waits for an answer — what the integration needs to know it asks
 * the other service again, as a query (the gates look again every so often).
 */
@Component
@RequiredArgsConstructor
public class Commands {

    /**
     * Which OPERA profile type each partner type is: certain when the partners come from Opera, so the
     * integration enters it rather than leaving it pending for a person. Keyed by the integration's
     * canonical type, which is what preparing a partner resolves.
     */
    static final Map<PartnerType, String> PARTNER_TYPES = new TreeMap<>(Map.of(
            PartnerType.TRAVEL_AGENT, "Agent",
            PartnerType.TOUR_OPERATOR, "Agent",
            PartnerType.COMPANY, "Company",
            PartnerType.ONLINE_AGENCY, "Source"));

    final Outbox outbox;

    /** The hotel's own equivalence in the mapping — which Opera property the CRS hotel is. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void defineHotel(String crsHotelCode, String pmsHotelCode, String by) {
        outbox.appendToMapping(new MappingCommand.DefineEquivalence(id(), CodeType.HOTEL, crsHotelCode, crsHotelCode,
                pmsHotelCode, by));
    }

    /** Which Opera profile type each partner type is, in the mapping (the chain's). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void definePartnerTypes(String by) {
        PARTNER_TYPES.forEach((type, operaType) -> outbox.appendToMapping(new MappingCommand.DefineEquivalence(id(),
                CodeType.PARTNER_TYPE, null, type.name(), operaType, by)));
    }

    /** Asks the mapping agent to propose the hotel's pending codes. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requestAgentProposal(String crsHotelCode) {
        outbox.appendToMapping(new MappingCommand.RequestAgentProposal(id(), crsHotelCode));
    }

    /** Resolves a cause in the mapping, if it is open: what held the processes waiting on it goes on. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resolveCauseIfOpen(String causeKey, String by) {
        outbox.appendToMapping(new MappingCommand.ResolveCauseIfOpen(id(), causeKey, by));
    }

    /** Which PMS profile a partner already is, for the mapping. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPartnerProfile(String partnerCode, String pmsProfileId, String profileType) {
        outbox.appendToMapping(new MappingCommand.RecordPartnerProfile(id(), partnerCode, pmsProfileId, profileType));
    }

    /** Announces a partner again in the master of partners, so that it is projected to the PMS. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resyncPartner(String partnerCode) {
        outbox.appendToPartners(new PartnerCommand.ResyncPartner(id(), partnerCode));
    }

    /** A partner as Opera has it, into the master of partners, with the Opera profile it is. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void importPartner(String partnerCode, String erpType, String name, String pmsProfileId, String profileType) {
        outbox.appendToPartners(new PartnerCommand.ImportPartner(id(), partnerCode, erpType, name, pmsProfileId, profileType));
    }

    /** A reservation for the CRS adapter to project by «Proyectar Reserva». */
    @Transactional(propagation = Propagation.MANDATORY)
    public void project(String crsHotelCode, String locator, String origin) {
        outbox.appendProjection(new ProjectReservation(id(), crsHotelCode, locator, origin));
    }

    static String id() {
        return UUID.randomUUID().toString();
    }
}
