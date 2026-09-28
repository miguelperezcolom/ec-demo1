package io.mateu.ecdemo1.mapping.ui.pages;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.core.domain.out.componentmapper.PageFormBuilder;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.config.TolerantReader;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Editing an equivalence decides its PMS side and its attributes: the PMS code (and a channel's
 * market code) is chosen among what the PMS offers for that type at that hotel; what identifies it
 * is read-only, and the decision on the version being replaced is not shown. Created by hand, the
 * type, hotel and CRS code are chosen too.
 */
class EntryEditTest {

    static final Map<String, List<CodeEntry>> PMS = Map.of(
            "XMAR", List.of(
                    new CodeEntry(CodeType.RATE_PLAN, "XMAR", "406484DIRXM", "DIRECTOS XMU A26"),
                    new CodeEntry(CodeType.RATE_PLAN, "XMAR", "RACK", "Rack rate"),
                    new CodeEntry(CodeType.CHANNEL, "XMAR", "WEB", "Source code: Web"),
                    new CodeEntry(CodeType.MARKET, "XMAR", "LEIS", "Market code: Leisure"),
                    new CodeEntry(CodeType.MARKET, "XMAR", "TOUR", "Market code: Tour operator")),
            "XPMI", List.of(
                    new CodeEntry(CodeType.RATE_PLAN, "XPMI", "RACK", "Rack rate")));

    final AtomicReference<Dictionary.Proposal> defined = new AtomicReference<>();

    final IntegrationClients clients = new IntegrationClients(
            new MappingProperties(null, null, null, null, null, Duration.ofSeconds(1)), new TolerantReader(new ObjectMapper())) {
        @Override
        public List<CodeEntry> pmsCatalog(String pmsHotelId) {
            return pmsHotelId == null ? List.of() : PMS.getOrDefault(pmsHotelId, List.of());
        }

        @Override
        public List<IntegrationView> integrations() {
            return List.of(new IntegrationView("1", "MRU01", "XMAR", IntegrationStatus.ACTIVE),
                    new IntegrationView("2", "PMI01", "XPMI", IntegrationStatus.ACTIVE));
        }

        @Override
        public Optional<IntegrationView> integration(String crsHotelCode) {
            return integrations().stream().filter(i -> i.crsHotelCode().equals(crsHotelCode)).findFirst();
        }
    };

    final Dictionary dictionary = new Dictionary(null, null, null, null) {
        @Override
        public Optional<Translation> resolve(String hotelCode, CodeType type, String code) {
            return Optional.empty();
        }

        @Override
        public MappingEntry define(Proposal proposal, String author) {
            defined.set(proposal);
            return MappingEntry.proposed("new-version", proposal.type(), proposal.hotelCode(), proposal.sourceCode(),
                    proposal.targetCode(), proposal.attributes(), author, null, null, Instant.EPOCH);
        }
    };

    final MappingEntryRepository entries = (MappingEntryRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{MappingEntryRepository.class}, (proxy, method, args) -> List.of());

    final Pending pending = new Pending(clients, dictionary, entries);

    EntryViewModel viewModel() {
        return new EntryViewModel(dictionary, pending);
    }

    static MappingEntry channel() {
        return approved(CodeType.CHANNEL, "MRU01", "WEB");
    }

    /** Version 2 of TTOO, in force. */
    static MappingEntry approved(CodeType type, String hotel, String target) {
        var e = MappingEntry.proposed("e1", type, hotel, "TTOO", target, Map.of("marketCode", "TOUR"), "agent", null, null,
                Instant.EPOCH);
        e.approve(1, "Ana", Instant.EPOCH);
        return e;
    }

    boolean readOnly(EntryViewModel vm, String field, boolean creating) throws Exception {
        return PageFormBuilder.isReadOnly(EntryViewModel.class.getDeclaredField(field), vm, creating, null);
    }

    @Test
    void editingAnEntryLeavesItsPmsSideAndAttributesEditable() throws Exception {
        var vm = viewModel().load(channel());
        for (var field : List.of("type", "hotelCode", "crsCode", "status", "version", "proposedBy", "decided")) {
            assertThat(readOnly(vm, field, false)).as(field).isTrue();
        }
        for (var field : List.of("pmsCode", "marketCode", "attributes")) {
            assertThat(readOnly(vm, field, false)).as(field).isFalse();
        }
    }

    @Test
    void theDecisionIsShownInTheDetailAndNotWhileEditing() {
        var vm = viewModel().load(channel());
        for (var field : List.of("id", "version", "proposedBy", "confidence", "rationale", "decided")) {
            assertThat(vm.isHidden(field, at("/mapping/dictionary/e1"))).as(field).isFalse();
            assertThat(vm.isHidden(field, at("/mapping/dictionary/e1/edit"))).as(field).isTrue();
        }
    }

    @Test
    void savingAnEditedEntryKeepsItsEditedAttributes() {
        var vm = viewModel().load(approved(CodeType.RATE_PLAN, "MRU01", "RACK"));
        assertThat(vm.attributes).containsExactly(new AttributeRow("marketCode", "TOUR"));
        vm.attributes = List.of(new AttributeRow("marketCode", "LEIS"), new AttributeRow("board", "AI"));
        vm.save(null);
        assertThat(defined.get().attributes()).containsExactly(Map.entry("marketCode", "LEIS"), Map.entry("board", "AI"));
    }

    /** A request for this route; everything else it is asked is the interface's default or null. */
    static HttpRequest at(String path) {
        return (HttpRequest) Proxy.newProxyInstance(EntryEditTest.class.getClassLoader(), new Class<?>[]{HttpRequest.class},
                (proxy, method, args) -> "path".equals(method.getName()) ? path
                        : method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : null);
    }

    @Test
    void creatingOneByHandChoosesTypeHotelAndCrsCodeToo() throws Exception {
        var vm = viewModel();
        for (var field : List.of("type", "hotelCode", "crsCode", "attributes", "pmsCode")) {
            assertThat(readOnly(vm, field, true)).as(field).isFalse();
        }
        // Nothing chosen yet to offer codes for: the PMS code is typed.
        assertThat(vm.stereotype("pmsCode", null)).isNull();
        assertThat(vm.isHidden("marketCode", null)).isTrue();
    }

    @Test
    void thePmsCodeIsASelectOfWhatThePropertyOffersForTheType() {
        var vm = viewModel().loadUnmapped("MRU01", CodeType.RATE_PLAN, "DIRECTA");
        assertThat(vm.stereotype("pmsCode", null)).isEqualTo(FieldStereotype.select);
        assertThat(vm.options("pmsCode", null)).extracting(Option::value, Option::label).containsExactly(
                org.assertj.core.groups.Tuple.tuple("406484DIRXM", "406484DIRXM — DIRECTOS XMU A26"),
                org.assertj.core.groups.Tuple.tuple("RACK", "RACK — Rack rate"));
        assertThat(vm.supports(String.class, "pmsCode", EntryViewModel.class)).isTrue();
        assertThat(vm.supports(String.class, "type", PmsCodeRow.class)).isFalse();
        assertThat(vm.options("type", null)).hasSize(CodeType.values().length);
    }

    @Test
    void aChannelsMarketCodeIsChosenTheSameWayAndSavingKeepsItAsAnAttribute() {
        var vm = viewModel().load(channel());
        assertThat(vm.attributes).isEmpty();
        assertThat(vm.marketCode).isEqualTo("TOUR");
        assertThat(vm.isHidden("marketCode", null)).isFalse();
        assertThat(vm.stereotype("marketCode", null)).isEqualTo(FieldStereotype.select);
        assertThat(vm.options("marketCode", null)).extracting(Option::value).containsExactly("LEIS", "TOUR");

        vm.pmsCode = "WEB";
        vm.marketCode = "LEIS";
        assertThat(vm.save(null)).isEqualTo("new-version");
        assertThat(defined.get().type()).isEqualTo(CodeType.CHANNEL);
        assertThat(defined.get().hotelCode()).isEqualTo("MRU01");
        assertThat(defined.get().sourceCode()).isEqualTo("TTOO");
        assertThat(defined.get().targetCode()).isEqualTo("WEB");
        assertThat(defined.get().attributes()).containsExactly(Map.entry("marketCode", "LEIS"));
    }

    @Test
    void theCodeInForceStaysAmongTheOptionsEvenIfThePmsNoLongerHasIt() {
        var e = approved(CodeType.RATE_PLAN, "MRU01", "OLD");
        var vm = viewModel().load(e);
        assertThat(vm.options("pmsCode", null)).extracting(Option::value).containsExactly("OLD", "406484DIRXM", "RACK");
        assertThat(vm.options("pmsCode", null).getFirst().label()).isEqualTo("OLD — not in the PMS catalog");
    }

    @Test
    void aChainLevelEntryIsOfferedEveryPropertysCodesSayingWhichHaveThem() {
        var e = approved(CodeType.RATE_PLAN, null, "RACK");
        var vm = viewModel().load(e);
        assertThat(vm.options("pmsCode", null)).extracting(Option::value, Option::label).containsExactly(
                org.assertj.core.groups.Tuple.tuple("406484DIRXM", "406484DIRXM — DIRECTOS XMU A26 (only XMAR)"),
                org.assertj.core.groups.Tuple.tuple("RACK", "RACK — Rack rate"));
    }
}
