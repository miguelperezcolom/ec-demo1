package io.mateu.ecdemo1.booking.infra.in.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UI("/_booking")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Booking")
@Logo("/images/riu.svg")
@Style(StyleConstants.CONTAINER)
@RequiredArgsConstructor
@Service
public class BookingHome {

    /**
     * "Call center", not "Booking": on the console this is the CRS's sales desk, and the label is
     * what the shell shows. The field name stays, because the routes hang from it.
     */
    @Menu
    @Label("Call center")
    BookingMenu booking;

}
