package io.mateu.ecdemo1.mapping.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.command.MappingCommand;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.messaging.Inbox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The commands the mapping takes, each once — without a broker or a database. */
class MappingCommandsTest {

    /** The inbox, in memory. */
    static class MemoryInbox extends Inbox {
        final Set<String> seen = new HashSet<>();

        MemoryInbox() {
            super(null, null, io.mateu.ecdemo1.messaging.MessagingProperties.defaults(), null, false);
        }

        @Override
        public boolean firstTime(String consumer, String eventId) {
            return seen.add(consumer + "/" + eventId);
        }
    }

    final List<String> done = new ArrayList<>();
    final MappingCommands commands = new MappingCommands(new MemoryInbox(), new MappingCommands.Effects() {
        @Override
        public void defineUnlessInForce(MappingCommand.DefineEquivalence c) {
            done.add("define " + c.codeType() + " " + c.hotelCode() + " " + c.sourceCode() + "=" + c.targetCode() + " by " + c.by());
        }

        @Override
        public void requestAgentProposal(String hotelCode) {
            done.add("agent " + hotelCode);
        }

        @Override
        public void resolveCauseIfOpen(String causeKey, String by) {
            done.add("resolve " + causeKey + " by " + by);
        }

        @Override
        public void recordPartnerProfile(String partnerCode, String pmsProfileId, String profileType) {
            done.add("profile " + partnerCode + " " + pmsProfileId + " " + profileType);
        }
    });

    @Test
    void eachCommandDoesWhatItSays() {
        commands.handle(new MappingCommand.DefineEquivalence("c1", CodeType.HOTEL, "NEW01", "NEW01", "RIUNEW", "integration NEW01"));
        commands.handle(new MappingCommand.RequestAgentProposal("c2", "NEW01"));
        commands.handle(new MappingCommand.ResolveCauseIfOpen("c3", "INTEGRATION_INACTIVE:NEW01", "ana"));
        commands.handle(new MappingCommand.RecordPartnerProfile("c4", "NORDTRAVEL", "16120699", "Company"));

        assertThat(done).containsExactly(
                "define HOTEL NEW01 NEW01=RIUNEW by integration NEW01",
                "agent NEW01",
                "resolve INTEGRATION_INACTIVE:NEW01 by ana",
                "profile NORDTRAVEL 16120699 Company");
    }

    @Test
    void aCommandDeliveredTwiceIsTakenOnce() {
        var command = new MappingCommand.RequestAgentProposal("c1", "NEW01");

        assertThat(commands.handle(command)).isTrue();
        assertThat(commands.handle(command)).isFalse();

        assertThat(done).containsExactly("agent NEW01");
    }

    @Test
    void aCommandWithNoIdIsRefused() {
        assertThatThrownBy(() -> commands.handle(new MappingCommand.RequestAgentProposal(" ", "NEW01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(done).isEmpty();
    }

    @Test
    void theIntegrationsServicesWireFormatIsRead() throws Exception {
        var json = new ObjectMapper();
        var command = json.readValue("""
                {"type":"define-equivalence","commandId":"c9","codeType":"PARTNER_TYPE","hotelCode":null,
                 "sourceCode":"TRAVEL_AGENT","targetCode":"Agent","by":"integration NEW01"}""", MappingCommand.class);

        commands.handle(command);

        assertThat(done).containsExactly("define PARTNER_TYPE null TRAVEL_AGENT=Agent by integration NEW01");
    }
}
