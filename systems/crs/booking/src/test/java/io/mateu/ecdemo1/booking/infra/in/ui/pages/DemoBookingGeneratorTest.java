package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.partners.PartnerDirectory;
import io.mateu.ecdemo1.booking.application.out.partners.PartnerDirectory.TradingPartner;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingTermsFactory;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.GuestType;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** The demo bookings: valid against the catalog, in the future, and sold through a partner when the channel says so. */
class DemoBookingGeneratorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    static final List<TradingPartner> PARTNERS = List.of(
            new TradingPartner("04900001", "TravelAgent", "TUI DEUTSCHLAND GMBH"),
            new TradingPartner("04410248", "TravelAgent", "TUI UK RETRASOS/STAFF/CREW"),
            new TradingPartner("NORDTRAVEL", "TourOperator", "Nordic Travel Group AB"),
            new TradingPartner("04900666", "Company", "BOOKING GERMANY"),
            new TradingPartner("09900002", "OnlineAgency", "PFRONT RIU CLASS IE"),
            new TradingPartner("04414660", "Company", "EMIRATES HOLIDAYS UK"));

    final CrsCatalog catalog = io.mateu.ecdemo1.booking.infra.out.catalog.ImportedCatalogs.standardCatalog();
    final RoomPricing pricing = new RoomPricing();
    final BookingTermsFactory terms = new BookingTermsFactory(catalog, pricing);

    DemoBookingGenerator generator(long seed, List<TradingPartner> partners) {
        return new DemoBookingGenerator(catalog, pricing, partners, new Random(seed), TODAY, "B1");
    }

    @Test
    void tenBookingsTheCrsAcceptsForMru01() {
        for (long seed = 0; seed < 50; seed++) {
            var bookings = generator(seed, PARTNERS).generate(10);

            assertThat(bookings).hasSize(10);
            var references = new HashSet<String>();
            for (var booking : bookings) {
                var request = booking.request();
                assertThat(booking.hotelCode()).isEqualTo("MRU01");
                // What the CRS does with it on creation: every code checked against the catalog, every room priced.
                var accepted = terms.terms(booking.hotelCode(), request);
                assertThat(accepted.rooms()).hasSizeBetween(1, 2);
                assertThat(request.arrival()).isBetween(TODAY.plusDays(14), TODAY.plusDays(56));
                assertThat(accepted.stay().nights()).isBetween(2, 7);
                assertThat(request.holder().email()).endsWith("@example.com");
                request.rooms().forEach(room -> {
                    assertThat(room.adults()).isBetween(1, 3);
                    assertThat(room.guests()).hasSize(room.adults() + room.childrenAges().size());
                });
                var channel = catalog.channel("MRU01", request.channelCode());
                assertThat(channel.code()).isIn("WEB", "CALLCENTER", "TTOO", "OTA");
                if (channel.requiresPartner()) {
                    assertThat(request.partnerCode()).isIn(PARTNERS.stream().map(TradingPartner::code).toList())
                            .isNotEqualTo("04410248");
                } else {
                    assertThat(request.partnerCode()).isNull();
                }
                // MRU01's own plans: a contract for a tour operator, an online agency's for OTA, direct otherwise.
                var ratePlans = switch (channel.code()) {
                    case "TTOO" -> List.of("TUI-NL", "TUI-FR", "DMC-MAURICIO", "AGENCIAS-LOCALES");
                    case "OTA" -> List.of("EXPEDIA-AD", "AGRO-MAYOR");
                    default -> List.of("DIRECTA", "FLEX-LOCAL");
                };
                assertThat(request.rooms()).allSatisfy(r -> {
                    assertThat(r.ratePlanCode()).isIn(ratePlans);
                    assertThat(r.boardCode()).isIn("SOLO-ALOJAMIENTO", "DESAYUNO", "COMIDAS", "TODO-INCLUIDO");
                    if (List.of("EXPEDIA-AD", "AGRO-MAYOR").contains(r.ratePlanCode())) {
                        assertThat(r.boardCode()).isEqualTo("DESAYUNO");
                    }
                });
                if ("OTA".equals(channel.code())) {
                    assertThat(request.partnerCode()).isEqualTo("04900666");
                }
                if (request.externalReference() != null) {
                    assertThat(references.add(request.externalReference())).isTrue();
                }
                booking.payments().forEach(p -> {
                    assertThat(channel.requiresPartner()).isFalse();
                    assertThat(catalog.paymentMethod("MRU01", p.methodCode())).isNotNull();
                    assertThat(p.amount()).isPositive().isLessThanOrEqualTo(accepted.total());
                });
            }
            assertThat(bookings).extracting(b -> b.request().channelCode())
                    .contains("WEB", "CALLCENTER", "TTOO", "OTA");
            assertThat(bookings).anySatisfy(b -> assertThat(b.payments()).isNotEmpty());
            assertThat(bookings).anySatisfy(b -> assertThat(b.request().rooms())
                    .anySatisfy(r -> assertThat(r.guests()).anySatisfy(g -> assertThat(g.type()).isEqualTo(GuestType.Child))));
            assertThat(bookings.stream().map(b -> b.request().holder().nationality()).distinct().count()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void theSameSeedGivesTheSameBookings() {
        assertThat(generator(42, PARTNERS).generate(10)).isEqualTo(generator(42, PARTNERS).generate(10));
    }

    @Test
    void withNoPartnersEveryBookingIsDirect() {
        var bookings = generator(7, List.of()).generate(10);

        assertThat(bookings).allSatisfy(b -> {
            assertThat(catalog.channel("MRU01", b.request().channelCode()).requiresPartner()).isFalse();
            assertThat(b.request().partnerCode()).isNull();
        });
    }

    @Test
    void oneRefusedBookingDoesNotStopTheRest() {
        var created = new ArrayList<CreateBookingCommand>();
        var create = new CreateBookingUseCase(null, null, null, null, null, null) {
            @Override
            public String handle(CreateBookingCommand command) {
                created.add(command);
                if (created.size() == 3) {
                    throw new IllegalArgumentException("refused");
                }
                return "B-" + created.size();
            }
        };
        PartnerDirectory directory = () -> PARTNERS;
        var form = new DemoBookingsForm(create, catalog, pricing, directory,
                Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC));

        var outcome = form.create(1L);

        assertThat(created).hasSize(10);
        assertThat(outcome.created()).hasSize(9).doesNotContain("B-3");
        assertThat(outcome.failed()).singleElement().asString().contains("#3").contains("refused");
        // Each booking is made with its payments, in the one command: nothing is paid afterwards.
        assertThat(created).anySatisfy(c -> assertThat(c.payments()).isNotEmpty());
        assertThat(outcome.message().text()).startsWith("9 of 10 demo bookings created");
    }

    @Test
    void unreadablePartnersLeaveTheBookingsDirect() {
        var create = new CreateBookingUseCase(null, null, null, null, null, null) {
            @Override
            public String handle(CreateBookingCommand command) {
                assertThat(command.booking().partnerCode()).isNull();
                return "B";
            }
        };
        PartnerDirectory directory = () -> {
            throw new IllegalStateException("connection refused");
        };
        var form = new DemoBookingsForm(create, catalog, pricing, directory, Clock.systemUTC());

        var outcome = form.create();

        assertThat(outcome.created()).hasSize(10);
        assertThat(outcome.notes()).singleElement().asString().contains("connection refused");
        assertThat(Objects.requireNonNull(outcome.message().text())).contains("all direct");
    }
}
