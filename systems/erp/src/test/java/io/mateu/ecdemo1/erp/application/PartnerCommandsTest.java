package io.mateu.ecdemo1.erp.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.erp.application.out.Inbox;
import io.mateu.ecdemo1.erp.application.out.PartnerRepository;
import io.mateu.ecdemo1.erp.application.usecases.PartnerCommands;
import io.mateu.ecdemo1.erp.application.usecases.PartnerService;
import io.mateu.ecdemo1.erp.domain.partner.Address;
import io.mateu.ecdemo1.erp.domain.partner.BillingMode;
import io.mateu.ecdemo1.erp.domain.partner.Partner;
import io.mateu.ecdemo1.erp.domain.partner.PartnerChanged;
import io.mateu.ecdemo1.erp.domain.partner.PartnerDetails;
import io.mateu.ecdemo1.erp.domain.partner.PartnerType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The commands the master takes, each once — without a broker or a database. */
class PartnerCommandsTest {

    /** The master's store in memory; what it would have put in the outbox, kept. */
    static class MemoryPartners implements PartnerRepository {
        final Map<String, Partner> partners = new HashMap<>();
        final List<PartnerChanged> announced = new ArrayList<>();

        @Override
        public Optional<Partner> findByCode(String code) {
            return Optional.ofNullable(partners.get(code));
        }

        @Override
        public Optional<Partner> findByCodeForUpdate(String code) {
            return findByCode(code);
        }

        @Override
        public List<Partner> search(String text, int page, int size) {
            return List.copyOf(partners.values());
        }

        @Override
        public PartnerPage search(String text, Collection<PartnerType> types, Collection<BillingMode> billingModes, Boolean active,
                                  int page, int size) {
            return new PartnerPage(List.copyOf(partners.values()), partners.size());
        }

        @Override
        public long count() {
            return partners.size();
        }

        @Override
        public void save(Partner partner) {
            partner.popEvents().forEach(e -> announced.add((PartnerChanged) e));
            partners.put(partner.getCode(), partner);
        }
    }

    static class MemoryInbox implements Inbox {
        final Set<String> seen = new HashSet<>();

        @Override
        public boolean firstTime(String consumer, String messageId) {
            return seen.add(consumer + "/" + messageId);
        }
    }

    final MemoryPartners store = new MemoryPartners();
    final PartnerService service = new PartnerService(store, Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC));
    final PartnerCommands commands = new PartnerCommands(new MemoryInbox(), service);

    void nordtravel() {
        service.create("NORDTRAVEL", new PartnerDetails(PartnerType.TravelAgent, "Nordtravel", "SE556677",
                new Address("Kungsgatan 10", "Stockholm", "11143", "SE"), "b@n.example", "+46", BillingMode.NoFront));
        store.announced.clear();
    }

    @Test
    void aPartnerTheMasterDoesNotHaveIsCreatedWithTheGuestPaying() {
        commands.handle(new PartnerCommands.Import("c1", "05100908", PartnerType.TravelAgent, "ABREU ONLINE PORTUGAL", "16120675", "Agent"));

        var p = store.partners.get("05100908");
        assertThat(p.getDetails().name()).isEqualTo("ABREU ONLINE PORTUGAL");
        assertThat(p.getDetails().type()).isEqualTo(PartnerType.TravelAgent);
        assertThat(p.getDetails().billingMode()).isEqualTo(BillingMode.Front);
        assertThat(p.getPmsProfile().profileId()).isEqualTo("16120675");
        assertThat(store.announced).hasSize(1);
    }

    @Test
    void aPartnerItHasKeepsWhatOnlyTheMasterKnows() {
        nordtravel();

        commands.handle(new PartnerCommands.Import("c1", "NORDTRAVEL", PartnerType.Company, "Nordtravel AB", "16120699", "Company"));

        var p = store.partners.get("NORDTRAVEL");
        assertThat(p.getDetails().type()).isEqualTo(PartnerType.Company);
        assertThat(p.getDetails().name()).isEqualTo("Nordtravel AB");
        assertThat(p.getDetails().taxId()).isEqualTo("SE556677");
        assertThat(p.getDetails().billingMode()).isEqualTo(BillingMode.NoFront);
        assertThat(p.getDetails().address().city()).isEqualTo("Stockholm");
        assertThat(p.getPmsProfile().profileId()).isEqualTo("16120699");
        assertThat(p.getVersion()).isEqualTo(2);
    }

    @Test
    void importedUnchangedItIsNotAnnouncedAgain() {
        nordtravel();

        commands.handle(new PartnerCommands.Import("c1", "NORDTRAVEL", PartnerType.TravelAgent, "Nordtravel", "16120699", "Agent"));

        assertThat(store.announced).isEmpty();
        assertThat(store.partners.get("NORDTRAVEL").getVersion()).isEqualTo(1);
        assertThat(store.partners.get("NORDTRAVEL").getPmsProfile().profileId()).isEqualTo("16120699");
    }

    @Test
    void aResyncIsTakenOnceHoweverOftenItIsDelivered() {
        nordtravel();
        var resync = new PartnerCommands.Resync("c1", "NORDTRAVEL");

        assertThat(commands.handle(resync)).isTrue();
        assertThat(commands.handle(resync)).isFalse();

        assertThat(store.announced).singleElement().satisfies(e -> assertThat(e.partnerCode()).isEqualTo("NORDTRAVEL"));
    }

    @Test
    void thePmsProfileIsRecordedWithoutAnnouncingThePartner() {
        nordtravel();

        commands.handle(new PartnerCommands.RecordPmsProfile("c1", "NORDTRAVEL", "16120699", "Agent"));

        assertThat(store.partners.get("NORDTRAVEL").getPmsProfile().profileId()).isEqualTo("16120699");
        assertThat(store.announced).isEmpty();
    }

    @Test
    void aPartnerTheMasterDoesNotHaveCannotBeResynced() {
        assertThatThrownBy(() -> commands.handle(new PartnerCommands.Resync("c1", "NOPE")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void theIntegrationsWireFormatIsRead() throws Exception {
        var json = new ObjectMapper();
        var command = json.readValue("""
                {"type":"import-partner","commandId":"c9","partnerCode":"05100908","partnerType":"TravelAgent",
                 "name":"ABREU","pmsProfileId":"16120675","profileType":"Agent"}""", PartnerCommands.Command.class);

        assertThat(command).isEqualTo(new PartnerCommands.Import("c9", "05100908", PartnerType.TravelAgent, "ABREU", "16120675", "Agent"));
    }
}
