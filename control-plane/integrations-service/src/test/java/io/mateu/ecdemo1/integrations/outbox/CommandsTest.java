package io.mateu.ecdemo1.integrations.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.command.MappingCommand;
import io.mateu.ecdemo1.integration.model.command.ProjectReservation;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integrations.clients.PartnerCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The commands to other services, as they go on the wire — without a broker or a database. */
class CommandsTest {

    /** The outbox, holding what it is given instead of writing it. */
    static class RecordingOutbox extends Outbox {
        final List<MappingCommand> mapping = new ArrayList<>();
        final List<PartnerCommand> partners = new ArrayList<>();
        final List<ProjectReservation> projections = new ArrayList<>();

        RecordingOutbox() {
            super(null, null, null, null);
        }

        @Override
        public void appendToMapping(MappingCommand command) {
            mapping.add(command);
        }

        @Override
        public void appendToPartners(PartnerCommand command) {
            partners.add(command);
        }

        @Override
        public void appendProjection(ProjectReservation request) {
            projections.add(request);
        }
    }

    final RecordingOutbox outbox = new RecordingOutbox();
    final Commands commands = new Commands(outbox);
    final ObjectMapper json = new ObjectMapper();

    @Test
    void thePartnerTypesAreFourEquivalencesOfTheChain() {
        commands.definePartnerTypes("integration NEW01");

        assertThat(outbox.mapping).hasSize(4).allSatisfy(c -> {
            var d = (MappingCommand.DefineEquivalence) c;
            assertThat(d.codeType()).isEqualTo(CodeType.PARTNER_TYPE);
            assertThat(d.hotelCode()).isNull();
            assertThat(d.key()).isEqualTo("chain");
        });
        assertThat(outbox.mapping).extracting(c -> ((MappingCommand.DefineEquivalence) c).sourceCode() + "="
                        + ((MappingCommand.DefineEquivalence) c).targetCode())
                .containsExactlyInAnyOrder("TRAVEL_AGENT=Agent", "TOUR_OPERATOR=Agent", "COMPANY=Company", "ONLINE_AGENCY=Source");
        assertThat(outbox.mapping).extracting(MappingCommand::commandId).doesNotHaveDuplicates();
    }

    @Test
    void aMappingCommandCarriesItsTypeAndTheMappingReadsItBack() throws Exception {
        commands.resolveCauseIfOpen("INTEGRATION_INACTIVE:NEW01", "ana");

        var sent = outbox.mapping.getFirst();
        var wire = json.writerFor(MappingCommand.class).writeValueAsString(sent);
        assertThat(wire).contains("\"type\":\"resolve-cause-if-open\"").contains("\"causeKey\":\"INTEGRATION_INACTIVE:NEW01\"")
                .doesNotContain("\"key\"");
        assertThat(json.readValue(wire, MappingCommand.class)).isEqualTo(sent);
        assertThat(sent.key()).isEqualTo("INTEGRATION_INACTIVE:NEW01");
    }

    @Test
    void aPartnerCommandIsInTheErpsTerms() throws Exception {
        commands.importPartner("05100908", "TravelAgent", "ABREU", "16120675", "Agent");

        var sent = outbox.partners.getFirst();
        var wire = json.readTree(json.writerFor(PartnerCommand.class).writeValueAsString(sent));
        assertThat(wire.path("type").asText()).isEqualTo("import-partner");
        assertThat(wire.path("partnerCode").asText()).isEqualTo("05100908");
        assertThat(wire.path("partnerType").asText()).isEqualTo("TravelAgent");
        assertThat(wire.path("pmsProfileId").asText()).isEqualTo("16120675");
        assertThat(wire.path("commandId").asText()).isNotBlank();
        assertThat(wire.has("key")).isFalse();
        assertThat(sent.key()).isEqualTo("05100908");
    }

    @Test
    void aProjectionIsKeyedByItsReservation() throws Exception {
        commands.project("NEW01", "R1", "backfill:run");

        var sent = outbox.projections.getFirst();
        assertThat(sent.key()).isEqualTo("NEW01/R1");
        assertThat(json.writeValueAsString(sent)).contains("\"origin\":\"backfill:run\"").doesNotContain("\"key\"");
    }

    @Test
    void whatTheRetiredHttpOutboxStillHeldIsCarriedOverAsCommands() {
        var retired = new RemoteCallTableRetired(null, commands, json);

        assertThat(retired.carryOver("DefineHotel", "{\"crsHotelCode\":\"NEW01\",\"pmsHotelCode\":\"RIUNEW\",\"by\":\"x\"}")).isTrue();
        assertThat(retired.carryOver("ResyncPartner", "{\"partnerCode\":\"NORDTRAVEL\"}")).isTrue();
        assertThat(retired.carryOver("ResolveCause", "{\"causeKey\":\"INTEGRATION_INACTIVE:NEW01\",\"by\":\"ana\"}")).isTrue();
        assertThat(retired.carryOver("RequestAgentProposal", "{\"crsHotelCode\":\"NEW01\"}")).isTrue();
        assertThat(retired.carryOver("Nonsense", "{}")).isFalse();
        assertThat(retired.carryOver("DefineHotel", "not json")).isFalse();

        assertThat(outbox.mapping).hasSize(3);
        assertThat(outbox.mapping.get(0)).isInstanceOfSatisfying(MappingCommand.DefineEquivalence.class, d -> {
            assertThat(d.codeType()).isEqualTo(CodeType.HOTEL);
            assertThat(d.sourceCode()).isEqualTo("NEW01");
            assertThat(d.targetCode()).isEqualTo("RIUNEW");
        });
        assertThat(outbox.mapping.get(1)).isInstanceOfSatisfying(MappingCommand.ResolveCauseIfOpen.class,
                r -> assertThat(r.by()).isEqualTo("ana"));
        assertThat(outbox.mapping.get(2)).isInstanceOf(MappingCommand.RequestAgentProposal.class);
        assertThat(outbox.partners).singleElement().isInstanceOfSatisfying(PartnerCommand.ResyncPartner.class,
                r -> assertThat(r.partnerCode()).isEqualTo("NORDTRAVEL"));
    }
}
