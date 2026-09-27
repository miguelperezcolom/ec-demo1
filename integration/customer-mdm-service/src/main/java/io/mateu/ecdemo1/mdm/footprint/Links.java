package io.mateu.ecdemo1.mdm.footprint;

import io.mateu.ecdemo1.mdm.config.MdmProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The addresses a person opens a customer's records at, in each system. Only base URLs: nothing
 * here carries a credential, so they can travel to a screen or to another service.
 */
@Component
public class Links {

    /** Where the data plane's Clientes section is mounted: the section field, then the entry. */
    public static final String CUSTOMERS_ROUTE = "/customers/search";
    /** Where the CRS's bookings are mounted on the data plane (booking's own @UI and menu). */
    public static final String BOOKINGS_ROUTE = "/booking/bookings";
    /** Where a booking's journey is drawn on the data plane (journey-service's own @UI and menu). */
    public static final String JOURNEY_ROUTE = "/journey/bookings";

    final String salesforce;
    final String frontOffice;

    public Links(LinksProperties links, MdmProperties mdm) {
        this.salesforce = blank(links.salesforceUrl())
                ? lightning(mdm.salesforce() == null ? null : mdm.salesforce().domain())
                : trim(links.salesforceUrl());
        this.frontOffice = blank(links.frontOfficePublicUrl()) ? null : trim(links.frontOfficePublicUrl());
    }

    /** A Salesforce record in Lightning — a Contact, a Case — or null when there is no org or no id. */
    public String salesforceRecord(String type, String id) {
        return salesforce == null || blank(id) ? null : salesforce + "/lightning/r/" + type + "/" + encode(id) + "/view";
    }

    /** A stay in the front office: its locator is the CRS booking's id. */
    public String frontOfficeStay(String locator) {
        return frontOffice == null || blank(locator) ? null : frontOffice + "/reservas/" + encode(locator);
    }

    /** A CRS booking on the data plane: a route of the same console, so relative. */
    public static String booking(String locator) {
        return BOOKINGS_ROUTE + "/" + encode(locator);
    }

    /** A CRS booking's journey across the chain on the data plane (journey-service, "Ver recorrido"). */
    public static String journey(String locator) {
        return JOURNEY_ROUTE + "/" + encode(locator);
    }

    /** A customer's page on the data plane (Clientes). */
    public static String customer(String id) {
        return CUSTOMERS_ROUTE + "/" + encode(id);
    }

    /**
     * The org's Lightning host from its My Domain: {@code acme.my.salesforce.com} is opened as
     * {@code acme.lightning.force.com}. Any other domain is used as it is — Salesforce redirects it.
     */
    static String lightning(String domain) {
        if (blank(domain)) {
            return null;
        }
        var host = domain.trim().replaceFirst("^https?://", "").replaceAll("/.*$", "").toLowerCase(Locale.ROOT);
        if (host.endsWith(".my.salesforce.com")) {
            host = host.substring(0, host.length() - ".my.salesforce.com".length()) + ".lightning.force.com";
        }
        return "https://" + host;
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String trim(String url) {
        return url.trim().replaceAll("/+$", "");
    }
}
