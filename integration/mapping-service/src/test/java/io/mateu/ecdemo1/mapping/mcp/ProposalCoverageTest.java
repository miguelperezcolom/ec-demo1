package io.mateu.ecdemo1.mapping.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.config.TolerantReader;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.proposals.ProposalAnnouncer;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent's first pass for MRU01 proposed 43 of its 45 pending codes and left out the two channels
 * with no source code of their own in XMAR (TTOO and OTA); nothing told it, and the notice only said
 * "43 proposals". The pending list comes whole, and proposing answers — to the agent and in the
 * notice — what is still without a proposal.
 */
class ProposalCoverageTest {

    static final String HOTEL = "MRU01";

    /** MRU01's shape: 15 room types, 9 rate plans, 4 boards, 8 channels, 5 payment methods, 4 reasons. */
    static final List<CodeEntry> CRS = Stream.of(
            IntStream.range(0, 15).mapToObj(i -> new CodeEntry(CodeType.ROOM_TYPE, HOTEL, "ROOM-" + i, "Room " + i)),
            IntStream.range(0, 9).mapToObj(i -> new CodeEntry(CodeType.RATE_PLAN, HOTEL, "RATE-" + i, "Rate " + i)),
            IntStream.range(0, 4).mapToObj(i -> new CodeEntry(CodeType.BOARD, HOTEL, "BOARD-" + i, "Board " + i)),
            Stream.of("WEB", "CALLCENTER", "TELEFONO", "EMAIL", "WALKIN", "GRUPOS")
                    .map(c -> new CodeEntry(CodeType.CHANNEL, HOTEL, c, c)),
            Stream.of(new CodeEntry(CodeType.CHANNEL, HOTEL, "TTOO", "Turoperador (contrato negociado)"),
                    new CodeEntry(CodeType.CHANNEL, HOTEL, "OTA", "Agencia de viajes online")),
            IntStream.range(0, 5).mapToObj(i -> new CodeEntry(CodeType.PAYMENT_METHOD, HOTEL, "PAY-" + i, "Pay " + i)),
            IntStream.range(0, 4).mapToObj(i -> new CodeEntry(CodeType.CANCELLATION_REASON, HOTEL, "CXL-" + i, "Cxl " + i)),
            // Another hotel's codes are not MRU01's to map.
            Stream.of(new CodeEntry(CodeType.ROOM_TYPE, "PMI01", "DBL", "Doble"))
    ).flatMap(s -> s).toList();

    final List<MappingEntry> proposed = new ArrayList<>();
    final List<Object[]> notices = new ArrayList<>();

    final MappingEntryRepository entries = (MappingEntryRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{MappingEntryRepository.class},
            (proxy, method, args) -> "findByStatusOrderByCreatedAtDesc".equals(method.getName()) ? List.copyOf(proposed) : List.of());

    final Dictionary dictionary = new Dictionary(null, null, null, null) {
        @Override
        public Optional<Translation> resolve(String hotelCode, CodeType type, String code) {
            return Optional.empty();
        }

        @Override
        public MappingEntry propose(Proposal p, String by) {
            var entry = MappingEntry.proposed(UUID.randomUUID().toString(), p.type(), p.hotelCode(), p.sourceCode(),
                    p.targetCode(), p.attributes(), by, p.confidence(), p.rationale(), Instant.now());
            proposed.add(entry);
            return entry;
        }
    };

    final Pending pending = new Pending(new IntegrationClients(
            new MappingProperties(null, null, null, null, null, Duration.ofSeconds(1)), new TolerantReader(new ObjectMapper())) {
        @Override
        public List<CodeEntry> crsCatalog() {
            return CRS;
        }
    }, dictionary, entries);

    final ProposalAnnouncer announcer = new ProposalAnnouncer(null, null, null) {
        @Override
        public void proposalsReady(int count, String hotelCode, List<String> leftOut) {
            notices.add(new Object[]{count, hotelCode, leftOut});
        }
    };

    final MappingMcpTools tools = new MappingMcpTools(null, dictionary, pending, entries, announcer);

    static Dictionary.Proposal proposalFor(CodeEntry e) {
        return new Dictionary.Proposal(e.type(), HOTEL, e.code(), "X-" + e.code(),
                e.type() == CodeType.CHANNEL ? Map.of("marketCode", "BAR") : Map.of(), 0.9, "because");
    }

    static List<CodeEntry> mru01() {
        return CRS.stream().filter(e -> HOTEL.equals(e.hotelCode())).toList();
    }

    @Test
    void thePendingListComesWholeInOneAnswer() {
        assertThat(mru01()).hasSize(45);
        assertThat(tools.listPendingCodes(HOTEL)).hasSize(45)
                .extracting(Pending.PendingCode::code).contains("TTOO", "OTA").doesNotContain("DBL");
    }

    @Test
    void codesLeftOutAreNamedToTheAgentAndInTheNotice() {
        var answer = tools.proposeMappings(mru01().stream()
                .filter(e -> !List.of("TTOO", "OTA").contains(e.code())).map(ProposalCoverageTest::proposalFor).toList(), null);

        assertThat(answer).startsWith("Proposed 43")
                .contains("still without a proposal in MRU01 (2): CHANNEL TTOO «Turoperador (contrato negociado)», "
                        + "CHANNEL OTA «Agencia de viajes online»")
                .contains("with low confidence");
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0)[0]).isEqualTo(43);
        assertThat(notices.get(0)[1]).isEqualTo(HOTEL);
        assertThat((List<String>) (List<?>) notices.get(0)[2]).containsExactly(
                "CHANNEL TTOO — the agent gave no proposal and no reason",
                "CHANNEL OTA — the agent gave no proposal and no reason");
        assertThat(pending.withoutProposal(HOTEL)).extracting(Pending.PendingCode::code).containsExactly("TTOO", "OTA");
    }

    @Test
    void aSecondCallWithTheRestCompletesIt() {
        tools.proposeMappings(mru01().stream().filter(e -> !List.of("TTOO", "OTA").contains(e.code()))
                .map(ProposalCoverageTest::proposalFor).toList(), null);

        var answer = tools.proposeMappings(mru01().stream().filter(e -> List.of("TTOO", "OTA").contains(e.code()))
                .map(ProposalCoverageTest::proposalFor).toList(), null);

        assertThat(answer).startsWith("Proposed 2").endsWith("; every pending code of MRU01 has a proposal");
        assertThat((List<String>) (List<?>) notices.get(1)[2]).isEmpty();
        assertThat(pending.withoutProposal(HOTEL)).isEmpty();
    }

    @Test
    void whatTheAgentCannotMatchGoesToTheNoticeWithItsReason() {
        var answer = tools.proposeMappings(mru01().stream().filter(e -> !"OTA".equals(e.code()))
                        .map(ProposalCoverageTest::proposalFor).toList(),
                List.of(new MappingMcpTools.Unmatched(CodeType.CHANNEL, null, "OTA", "XMAR has no OTA source code")));

        assertThat(answer).startsWith("Proposed 44")
                .endsWith("; every pending code of MRU01 has a proposal, but 1 you left unmatched: "
                        + "CHANNEL OTA — XMAR has no OTA source code");
        assertThat((List<String>) (List<?>) notices.get(0)[2]).containsExactly("CHANNEL OTA — XMAR has no OTA source code");
    }

    @Test
    void nothingProposedButSomethingUnmatchedStillTellsWhoReviews() {
        tools.proposeMappings(List.of(), List.of(new MappingMcpTools.Unmatched(CodeType.CHANNEL, HOTEL, "OTA", "no source")));

        assertThat(notices).hasSize(1);
        assertThat(notices.get(0)[0]).isEqualTo(0);
        assertThat((List<String>) (List<?>) notices.get(0)[2]).contains("CHANNEL OTA — no source");
    }

    @Test
    void theNoticeSaysWhatIsLeft() {
        assertThat(ProposalAnnouncer.title(43, List.of("CHANNEL TTOO — x", "CHANNEL OTA — y")))
                .isEqualTo("43 mapping proposal(s) to review, 2 code(s) left without one");
        assertThat(ProposalAnnouncer.body(43, List.of("CHANNEL TTOO — x")))
                .endsWith("Still without a proposal, so the integration keeps waiting for a mapping: CHANNEL TTOO — x.");
        assertThat(ProposalAnnouncer.title(45, List.of())).isEqualTo("45 mapping proposal(s) to review");
    }
}
