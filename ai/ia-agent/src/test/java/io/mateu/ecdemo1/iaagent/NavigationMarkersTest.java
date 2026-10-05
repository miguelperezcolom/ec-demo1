package io.mateu.ecdemo1.iaagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The answer that left the user with «Nora Duarte: » and nothing after it: the model wrote one
 * NAVIGATE marker per booking, inline, as if they were links. Every marker was stripped and every
 * one navigated.
 */
class NavigationMarkersTest {

    static final ObjectMapper JSON = new ObjectMapper();

    static String marker(String route) {
        return "[NAVIGATE:{\"route\":\"" + route + "\",\"consumedRoute\":\"\",\"actionId\":\"\","
                + "\"baseUrl\":\"/_booking\",\"serverSideType\":\"io.mateu.ecdemo1.booking.infra.in.ui.BookingHome\","
                + "\"uriPrefix\":\"\"}]";
    }

    /** The answer as the agent wrote it in the conversation that was reported. */
    static final String INLINE_LINKS_ANSWER = "No, no admite filtro por ids. Para consultar una reserva concreta, "
            + "abre su ficha individual:\n\n"
            + "- Nora Duarte: " + marker("/booking/bookings/4MBZS7") + "\n"
            + "- Giulia Okafor: " + marker("/booking/bookings/JXD3G6") + "\n";

    @Test
    void inlineMarkersBecomeLinksAndNothingNavigates() {
        var parsed = NavigationMarkers.parse(INLINE_LINKS_ANSWER, JSON);

        assertTrue(parsed.cleanText().contains("- Nora Duarte: [4MBZS7](/booking/bookings/4MBZS7)"), parsed.cleanText());
        assertTrue(parsed.cleanText().contains("- Giulia Okafor: [JXD3G6](/booking/bookings/JXD3G6)"), parsed.cleanText());
        assertFalse(parsed.cleanText().contains("NAVIGATE"));
        assertTrue(parsed.navigations().isEmpty());
    }

    @Test
    void aMarkerOnItsOwnLineNavigates() {
        var parsed = NavigationMarkers.parse("Hay 2 reservas canceladas; te las muestro en el listado.\n"
                + marker("/booking/bookings?status=Cancelled"), JSON);

        assertEquals("Hay 2 reservas canceladas; te las muestro en el listado.", parsed.cleanText());
        assertEquals(1, parsed.navigations().size());
        assertTrue(parsed.navigations().get(0).contains("\"route\":\"/booking/bookings?status=Cancelled\""));
    }

    @Test
    void aSingleInlineMarkerStillNavigatesAsBefore() {
        var parsed = NavigationMarkers.parse("Te abro el listado. " + marker("/booking/bookings"), JSON);

        assertEquals("Te abro el listado.", parsed.cleanText());
        assertEquals(1, parsed.navigations().size());
    }

    @Test
    void ofSeveralCommandsOnlyTheLastNavigatesAndTheOthersStayAsLinks() {
        var parsed = NavigationMarkers.parse(marker("/booking/bookings/4MBZS7") + "\n"
                + marker("/booking/bookings?ids=4MBZS7,JXD3G6"), JSON);

        assertEquals("[4MBZS7](/booking/bookings/4MBZS7)", parsed.cleanText());
        assertEquals(1, parsed.navigations().size());
        assertTrue(parsed.navigations().get(0).contains("ids=4MBZS7,JXD3G6"));
    }

    @Test
    void aMalformedMarkerIsDroppedAndNeverNavigates() {
        var parsed = NavigationMarkers.parse("Hola [NAVIGATE:{route:x}] adiós", JSON);

        assertEquals("Hola  adiós", parsed.cleanText());
        assertTrue(parsed.navigations().isEmpty());
    }

    @Test
    void theStreamShowsTheSameLinksTheFinalAnswerHas() {
        // in small chunks, as the model streams it, splitting the markers anywhere
        var chunks = new ArrayList<String>();
        for (int i = 0; i < INLINE_LINKS_ANSWER.length(); i += 7) {
            chunks.add(INLINE_LINKS_ANSWER.substring(i, Math.min(INLINE_LINKS_ANSWER.length(), i + 7)));
        }
        var streamed = NavigationMarkerFilterTest.through(chunks);

        // the second link is known to be a link while streaming; the first may still have been the
        // answer's only marker, so it shows once the final text replaces the streamed one
        assertTrue(streamed.contains("- Giulia Okafor: [JXD3G6](/booking/bookings/JXD3G6)"), streamed);
        assertFalse(streamed.contains("NAVIGATE"), streamed);
        assertTrue(NavigationMarkers.parse(INLINE_LINKS_ANSWER, JSON).cleanText()
                .contains("[4MBZS7](/booking/bookings/4MBZS7)"));
    }

    @Test
    void theLinkIsLabelledWithTheRoutesLastSegment() {
        assertEquals("[4MBZS7](/booking/bookings/4MBZS7)", NavigationMarkers.link("{\"route\":\"/booking/bookings/4MBZS7\"}"));
        assertEquals("[bookings](/booking/bookings?status=Cancelled)",
                NavigationMarkers.link("{\"route\":\"/booking/bookings?status=Cancelled\"}"));
        assertNull(NavigationMarkers.link("{\"route\":\"\"}"));
    }
}
