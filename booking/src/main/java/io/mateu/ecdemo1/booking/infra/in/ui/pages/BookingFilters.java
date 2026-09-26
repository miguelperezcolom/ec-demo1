package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.DateRange;
import io.mateu.uidl.data.FieldStereotype;

import java.util.Set;

/**
 * Each field is a filter in the search bar; together with the free text they narrow the bookings.
 * The hotel's options come from the CRS catalog, supplied by {@link BookingCrudOrchestrator}.
 */
public class BookingFilters {

    @Stereotype(FieldStereotype.select)
    String hotel;
    Set<BookingStatus> status;
    DateRange arrival;
    DateRange departure;
}
