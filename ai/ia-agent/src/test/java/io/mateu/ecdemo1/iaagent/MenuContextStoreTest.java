package io.mateu.ecdemo1.iaagent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuContextStoreTest {

    static final ChatRequest.NavigationDetail BOOKINGS_NAV = new ChatRequest.NavigationDetail("/booking/bookings",
            "", "", "/_booking", "io.mateu.ecdemo1.booking.infra.in.ui.BookingHome", "");

    static final ChatRequest.ListingInfo BOOKINGS_LISTING = new ChatRequest.ListingInfo("id", "ids", "searchText",
            List.of(new ChatRequest.ListingFilter("hotel", "Hotel", "string", false, null, null, null),
                    new ChatRequest.ListingFilter("status", "Status", "enum", true,
                            List.of("Pending", "Confirmed", "Cancelled"), null, null),
                    new ChatRequest.ListingFilter("arrival", "Arrival", "dateRange", false, null,
                            "arrival_from", "arrival_to")));

    @Test
    void aListingTellsTheAgentItsUrlFiltersAndTheIdSet() {
        var store = new MenuContextStore();
        store.update("s", List.of(new ChatRequest.MenuEntry(List.of("Call center", "Bookings"), BOOKINGS_NAV,
                null, BOOKINGS_LISTING)));

        var prompt = store.buildMenuSystemPrompt("s", "/booking/bookings");

        assertTrue(prompt.contains("`status` (Status, uno o varios separados por comas de: Pending|Confirmed|Cancelled)"), prompt);
        assertTrue(prompt.contains("`arrival_from` / `arrival_to` (Arrival, rango fecha ISO yyyy-MM-dd"), prompt);
        assertTrue(prompt.contains("`hotel` (Hotel, texto)"), prompt);
        assertTrue(prompt.contains("`searchText` (búsqueda de texto libre)"), prompt);
        assertTrue(prompt.contains("`ids` (un conjunto concreto"), prompt);
        assertTrue(prompt.contains("identificador de fila: `id`"), prompt);
        assertTrue(prompt.contains("/booking/bookings?ids=4MBZS7,JXD3G6"), prompt);
        assertTrue(prompt.contains("El usuario está viendo ahora mismo la ruta `/booking/bookings`"), prompt);
        assertFalse(prompt.contains("a la izquierda"), prompt);
        assertTrue(prompt.contains("No describas dónde está nada"), prompt);
    }

    @Test
    void aShellWithoutListingMetadataGetsTheMenuAsBefore() {
        var store = new MenuContextStore();
        store.update("s", List.of(new ChatRequest.MenuEntry(List.of("Call center", "Bookings"), BOOKINGS_NAV)));

        var prompt = store.buildMenuSystemPrompt("s");

        assertTrue(prompt.contains("**Call center > Bookings**"), prompt);
        assertTrue(prompt.contains("[NAVIGATE:{\"route\":\"/booking/bookings\""), prompt);
        assertFalse(prompt.contains("Enseñar elementos en un listado"), prompt);
        assertFalse(prompt.contains("Es un listado"), prompt);
    }

    @Test
    void theScreenRouteFallsBackToTheChatsContextUrl() {
        var request = new ChatRequest("hola", "s", null, null, null,
                java.util.Map.of("url", "/booking/bookings?status=Cancelled"));
        assertEquals("/booking/bookings?status=Cancelled", request.screenRoute());
        assertEquals("/x", new ChatRequest("hola", "s", null, "/x", null).screenRoute());
    }
}
