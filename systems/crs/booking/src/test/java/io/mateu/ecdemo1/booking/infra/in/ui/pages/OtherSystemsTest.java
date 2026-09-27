package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.infra.out.mdm.CustomerLinks;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** A booking's links to the chain's other systems, from what the customer MDM says of its people. */
class OtherSystemsTest {

    static final CustomerLinks.ReservationLinks LINKS = new CustomerLinks.ReservationLinks("MRU01", "B1",
            List.of(new CustomerLinks.Passenger(0, "HOLDER", "C-ANA", "Ana <García>", "PROVISIONAL",
                            "/customers/search/C-ANA", "003ANA", "https://acme.lightning.force.com/lightning/r/Contact/003ANA/view"),
                    new CustomerLinks.Passenger(1, "GUEST", "C-LEO", "Leo García", "PROVISIONAL",
                            "/customers/search/C-LEO", null, null)),
            List.of(new CustomerLinks.OperaProfile("20538296", "C-ANA", "XMAR/B1")),
            new CustomerLinks.FrontOfficeStay("B1", "IN_HOUSE", "https://front.ec1.mateu.io/reservas/B1"));

    @Test
    void aBookingLinksToItsStayItsCustomersAndTheirContactsAndNamesItsOperaIds() {
        assertThat(OtherSystems.rows("98765", LINKS)).extracting(Html.Link::what, Html.Link::label, Html.Link::href)
                .containsExactly(
                        // Opera Cloud has no stable deep link: its ids are references to copy.
                        tuple("Opera · reservation", "98765", null),
                        tuple("Opera · guest profile", "20538296", null),
                        tuple("Front office · stay", "In house", "https://front.ec1.mateu.io/reservas/B1"),
                        tuple("Holder · customer", "Ana <García> · C-ANA", "/customers/search/C-ANA"),
                        tuple("Holder · Salesforce", "Contact 003ANA", "https://acme.lightning.force.com/lightning/r/Contact/003ANA/view"),
                        tuple("Guest 2 · customer", "Leo García · C-LEO", "/customers/search/C-LEO"));
    }

    @Test
    void theLinksAreMarkupWithTheDataEscapedAndOnlyTheOtherSitesInANewTab() {
        var html = ((Element) OtherSystems.of(null, LINKS)).content();
        assertThat(html).contains("Ana &lt;García&gt;").doesNotContain("<García>")
                .contains("<a href=\"/customers/search/C-ANA\">")
                .contains("<a href=\"https://front.ec1.mateu.io/reservas/B1\" target=\"_blank\"");
    }

    @Test
    void withoutTheMdmOnlyWhatTheBookingKnowsIsShown() {
        assertThat(OtherSystems.rows("98765", null)).extracting(Html.Link::what).containsExactly("Opera · reservation");
        assertThat(OtherSystems.of(null, null)).isInstanceOf(Text.class);
    }
}
