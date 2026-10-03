package io.mateu.ecdemo1.iaagent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavigationMarkerFilterTest {

    static String through(List<String> chunks) {
        var filter = new NavigationMarkerFilter();
        var sb = new StringBuilder();
        chunks.forEach(c -> sb.append(filter.feed(c)));
        return sb.append(filter.finish()).toString();
    }

    @Test
    void plainTextPassesAsItComes() {
        var filter = new NavigationMarkerFilter();
        assertEquals("Hola, ", filter.feed("Hola, "));
        assertEquals("¿qué tal?", filter.feed("¿qué tal?"));
    }

    @Test
    void aWholeMarkerIsDropped() {
        assertEquals("Te llevo.  Hecho.", through(List.of("Te llevo. [NAVIGATE:{\"route\":\"/x\"}] Hecho.")));
    }

    @Test
    void aMarkerSplitAcrossChunksNeverShows() {
        var filter = new NavigationMarkerFilter();
        assertEquals("Te llevo ", filter.feed("Te llevo ["));
        assertEquals("", filter.feed("NAVI"));
        assertEquals("", filter.feed("GATE:{\"route\":"));
        assertEquals(" ya.", filter.feed("\"/x\"}] ya."));
    }

    @Test
    void aBracketThatIsNotAMarkerIsReleased() {
        var filter = new NavigationMarkerFilter();
        assertEquals("Ver ", filter.feed("Ver [N"));
        assertEquals("[Nota] aquí", filter.feed("ota] aquí"));
        assertEquals("[1] y [2]", through(List.of("[1] y [", "2]")));
    }

    @Test
    void theEndReleasesAnUnfinishedBracketAndDropsAnUnclosedMarker() {
        assertEquals("Precio [NAV", through(List.of("Precio [NAV")));
        assertEquals("Te llevo ", through(List.of("Te llevo [NAVIGATE:{\"route\":")));
    }
}
