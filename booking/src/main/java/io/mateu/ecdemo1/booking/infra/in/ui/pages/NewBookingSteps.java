package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.wizard.WizardStep;
import io.mateu.ecdemo1.booking.infra.in.ui.suppliers.CatalogLookup;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.DetailFormCustomisation;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.FormPosition;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

// The steps of NewBookingWizard, one record each. Field names are unique across the steps: the
// wizard's state is one flat map.

/** Where and when, through which channel, and who holds the booking. */
record StayStep(
        @Section(value = "Stay", columns = 2)
        @NotEmpty @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String hotelCode,
        @NotEmpty @Lookup(search = CatalogLookup.class, label = CatalogLookup.class) String channelCode,
        String partnerCode,
        String externalReference,
        @NotNull LocalDate arrival,
        @NotNull LocalDate departure,
        @Section(value = "Holder", columns = 2)
        @NotEmpty String holderFirstName,
        @NotEmpty String holderLastName,
        String holderEmail,
        String holderPhone,
        String holderNationality,
        @Section("Comments")
        @Stereotype(FieldStereotype.textarea) @Colspan(2) String comments)
        implements WizardStep {
}

/** The rooms, numbered in the order they are listed. Prices are the CRS's, when it is created. */
record RoomsStep(
        @DetailFormCustomisation(position = FormPosition.modal) @Colspan(2) List<RoomViewModel> rooms)
        implements WizardStep {
}

/** Who stays, each in the line of their room. */
record GuestsStep(
        @DetailFormCustomisation(position = FormPosition.modal) @Colspan(2) List<GuestViewModel> guests)
        implements WizardStep {
}

/** What is paid already, if anything. Registered once the booking exists. */
record PaymentsStep(
        @DetailFormCustomisation(position = FormPosition.modal) @Colspan(2) List<PaymentViewModel> payments)
        implements WizardStep {
}

/** The booking as it will be asked for, to read before creating it. */
@ReadOnly
record SummaryStep(
        @Section(value = "Summary", columns = 1) String booking,
        String holder,
        @Stereotype(FieldStereotype.textarea) String roomsSummary,
        @Stereotype(FieldStereotype.textarea) String guestsSummary,
        @Stereotype(FieldStereotype.textarea) String paymentsSummary)
        implements WizardStep {
}

/** The wizard's result step. Not seen: creating the booking goes to it straight away. */
@ReadOnly
record CreatedStep(String message) implements WizardStep {
}
