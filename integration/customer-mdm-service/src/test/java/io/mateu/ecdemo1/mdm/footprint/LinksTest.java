package io.mateu.ecdemo1.mdm.footprint;

import io.mateu.ecdemo1.mdm.config.MdmProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LinksTest {

    static Links links(String salesforceUrl, String domain, String frontOffice) {
        return new Links(new LinksProperties(null, null, frontOffice, salesforceUrl, null),
                new MdmProperties(new MdmProperties.Salesforce(domain, "id", "secret", "v67.0", null, 0, false),
                        Duration.ofSeconds(5), Duration.ofSeconds(60)));
    }

    @Test
    void theOrgIsOpenedInLightningFromItsMyDomain() {
        assertThat(links(null, "acme-dev-ed.develop.my.salesforce.com", null).salesforceRecord("Contact", "003X"))
                .isEqualTo("https://acme-dev-ed.develop.lightning.force.com/lightning/r/Contact/003X/view");
        assertThat(links(null, "https://acme.my.salesforce.com/", null).salesforceRecord("Case", "500X"))
                .isEqualTo("https://acme.lightning.force.com/lightning/r/Case/500X/view");
        // Said outright, it wins.
        assertThat(links("https://other.lightning.force.com/", "acme.my.salesforce.com", null).salesforceRecord("Contact", "003X"))
                .isEqualTo("https://other.lightning.force.com/lightning/r/Contact/003X/view");
    }

    @Test
    void thereIsNoLinkWithoutAnOrgOrAnId() {
        assertThat(links(null, "", null).salesforceRecord("Contact", "003X")).isNull();
        assertThat(links(null, "acme.my.salesforce.com", null).salesforceRecord("Contact", null)).isNull();
        assertThat(links(null, null, null).frontOfficeStay("B1")).isNull();
    }

    @Test
    void aStayAndABookingAreOpenedByTheirLocator() {
        assertThat(links(null, null, "https://front.ec1.mateu.io/").frontOfficeStay("a b"))
                .isEqualTo("https://front.ec1.mateu.io/reservas/a%20b");
        assertThat(Links.booking("B1")).isEqualTo("/booking/bookings/B1");
        assertThat(Links.customer("C-1")).isEqualTo("/customers/search/C-1");
    }
}
