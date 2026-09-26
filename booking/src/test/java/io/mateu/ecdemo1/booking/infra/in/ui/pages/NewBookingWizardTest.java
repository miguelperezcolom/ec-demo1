package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.query.BookingQueryService;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.cancel.CancelBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.create.CreateBookingUseCase;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.payment.RegisterPaymentUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.GuestType;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.uidl.data.UICommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The new-booking wizard's own rules, and cancelling instead of deleting. Fakes rather than mocks:
 * Mockito cannot instrument classes on this JDK.
 */
class NewBookingWizardTest {

    static final LocalDate ARRIVAL = LocalDate.of(2026, 11, 2);

    final List<CreateBookingCommand> created = new ArrayList<>();
    final List<RegisterPaymentCommand> paid = new ArrayList<>();
    final CreateBookingUseCase create = new CreateBookingUseCase(null, null, null, null, null, null) {
        @Override
        public String handle(CreateBookingCommand command) {
            created.add(command);
            return "B-1";
        }
    };
    final RegisterPaymentUseCase pay = new RegisterPaymentUseCase(null, null, null) {
        @Override
        public String handle(RegisterPaymentCommand command) {
            paid.add(command);
            return "P-1";
        }
    };
    final NewBookingWizard wizard = new NewBookingWizard(create, pay, CrsCatalog.standard());

    static StayStep stay(String channel, String partner, LocalDate arrival, LocalDate departure) {
        return new StayStep("MRU01", channel, partner, null, arrival, departure,
                "Ana", "García", "ana@example.com", null, "ES", null);
    }

    static RoomViewModel room() {
        return new RoomViewModel(null, "DBL", "BAR", "AD", 2, null, null);
    }

    static GuestViewModel guest(int roomLine) {
        return new GuestViewModel(roomLine, "Ana", "García", GuestType.Adult, null, null, null, null, null);
    }

    @Test
    void theListActionsOfAStepAreTheStepsLists() {
        assertThat(WizardStepLists.listField(RoomsStep.class, "rooms_add")).isNotNull();
        assertThat(WizardStepLists.listField(RoomsStep.class, "rooms_create-and-stay").getName()).isEqualTo("rooms");
        assertThat(WizardStepLists.listField(GuestsStep.class, "guests_remove")).isNotNull();
        assertThat(WizardStepLists.listField(PaymentsStep.class, "payments_save")).isNotNull();
        // not a list action, or not a list of this step: the wizard's own flow handles it
        assertThat(WizardStepLists.listField(RoomsStep.class, "next")).isNull();
        assertThat(WizardStepLists.listField(RoomsStep.class, "guests_add")).isNull();
        assertThat(WizardStepLists.listField(RoomsStep.class, "rooms_frobnicate")).isNull();
        assertThat(WizardStepLists.listField(StayStep.class, "hotelCode_add")).isNull();
    }

    @Test
    void noStepIsNamedLikeTheListItHolds() {
        // The wizard's state is one flat map: a list named like its step overwrote the step.
        for (var step : NewBookingWizard.class.getDeclaredFields()) {
            if (io.mateu.core.infra.declarative.orchestrators.wizard.WizardStep.class.isAssignableFrom(step.getType())) {
                for (var field : step.getType().getDeclaredFields()) {
                    assertThat(field.getName()).as(step.getName()).isNotEqualTo(step.getName());
                }
            }
        }
    }

    @Test
    void theStayNeedsADepartureAfterTheArrival() {
        wizard.stay = stay("WEB", null, ARRIVAL, ARRIVAL);
        assertThat(wizard.problemLeaving("stay")).contains("after the arrival");
    }

    @Test
    void aChannelSellingThroughPartnersNeedsOne() {
        wizard.stay = stay("TTOO", null, ARRIVAL, ARRIVAL.plusDays(3));
        assertThat(wizard.problemLeaving("stay")).contains("partner");

        wizard.stay = stay("TTOO", "ECDEMO0001", ARRIVAL, ARRIVAL.plusDays(3));
        assertThat(wizard.problemLeaving("stay")).isNull();
    }

    @Test
    void aBookingHasARoomAtLeast() {
        wizard.roomsStep = new RoomsStep(List.of());
        assertThat(wizard.problemLeaving("roomsStep")).isEqualTo("Add at least one room");

        wizard.roomsStep = new RoomsStep(List.of(room()));
        assertThat(wizard.problemLeaving("roomsStep")).isNull();
    }

    @Test
    void aGuestIsInOneOfTheRooms() {
        wizard.roomsStep = new RoomsStep(List.of(room()));
        wizard.guestsStep = new GuestsStep(List.of(guest(2)));
        assertThat(wizard.problemLeaving("guestsStep")).contains("is in room 2, and the booking has 1 room(s)");
    }

    @Test
    void theSummaryReadsTheSteps() {
        wizard.stay = stay("WEB", null, ARRIVAL, ARRIVAL.plusDays(3));
        wizard.roomsStep = new RoomsStep(List.of(room()));
        wizard.guestsStep = new GuestsStep(List.of(guest(1)));

        var summary = wizard.summarise();

        assertThat(summary.booking()).startsWith("Riu Demo Mauricio");
        assertThat(summary.roomsSummary()).isEqualTo("1. DBL · BAR · AD · 2 adult(s)");
        assertThat(summary.guestsSummary()).isEqualTo("Room 1: Ana García (Adult)");
        assertThat(summary.paymentsSummary()).isEqualTo("None");
    }

    @Test
    void creatingGoesThroughTheUseCasesAndOpensTheBooking() {
        wizard.stay = stay("WEB", null, ARRIVAL, ARRIVAL.plusDays(3));
        wizard.roomsStep = new RoomsStep(List.of(room()));
        wizard.guestsStep = new GuestsStep(List.of(guest(1)));
        wizard.paymentsStep = new PaymentsStep(List.of(
                new PaymentViewModel(null, PaymentType.Deposit, "VISA", new BigDecimal("100"), null, null)));

        var result = (List<?>) wizard.createBooking();

        assertThat(created).singleElement().satisfies(command -> {
            assertThat(command.hotelCode()).isEqualTo("MRU01");
            assertThat(command.booking().rooms()).singleElement()
                    .satisfies(r -> assertThat(r.guests()).hasSize(1));
        });
        assertThat(paid).singleElement().satisfies(payment -> assertThat(payment.id()).isEqualTo("B-1"));
        assertThat(result).last().isEqualTo(UICommand.navigateTo("/booking/bookings/B-1"));
    }

    @Test
    void cancellingSkipsWhatIsCancelledAlreadyAndGoesOnAfterARefusal() {
        var asked = new ArrayList<CancelBookingCommand>();
        var cancel = new CancelBookingUseCase(null, null, null) {
            @Override
            public void handle(CancelBookingCommand command) {
                asked.add(command);
                if (command.id().equals("C")) {
                    throw new IllegalStateException("refused");
                }
            }
        };
        var statuses = Map.of("A", BookingStatus.Confirmed, "B", BookingStatus.Cancelled,
                "C", BookingStatus.Pending, "D", BookingStatus.Pending);
        var query = (BookingQueryService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BookingQueryService.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getById")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    return Optional.ofNullable(statuses.get((String) args[0])).map(NewBookingWizardTest::booking);
                });
        var form = new BookingCancellationForm(cancel, query);
        form.cancellationReasonCode = "CLI";

        var outcome = form.cancel(List.of("A", "B", "C", "D"));

        assertThat(outcome.cancelled()).containsExactly("A", "D");
        assertThat(outcome.alreadyCancelled()).containsExactly("B");
        assertThat(outcome.refused()).containsExactly("C (refused)");
        assertThat(asked).extracting(CancelBookingCommand::id).containsExactly("A", "C", "D");
    }

    static BookingDto booking(BookingStatus status) {
        return new BookingDto(null, null, null, status, 0, null, null, null, null, null, 0, null, List.of(),
                List.of(), null, null, null, null, null, null, null, null);
    }
}
