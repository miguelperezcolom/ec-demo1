package io.mateu.ecdemo1.shell.infra.in.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The home's tile says which room types Opera runs out of, and when; the page's grid, each night's count. */
class OperaRoomsTest {

    static final String ANSWER = """
            {"hotelId": "XMAR", "from": "2026-11-16", "cap": 5, "roomTypes": ["DBPB", "SJLB", "STDK"],
             "nights": [{"date": "2026-11-16", "free": {"SJLB": 2, "STDK": 5}},
                        {"date": "2026-11-17", "free": {"STDK": 5}},
                        {"date": "2026-11-18", "free": {"DBPB": 1, "SJLB": 1, "STDK": 4}}],
             "readAt": "2026-11-16T08:00:00Z"}""";

    @Test
    void theTileNamesTheRoomTypesThatRunOutAndWhen() throws Exception {
        var calendar = OperaRooms.calendar(new ObjectMapper().readTree(ANSWER));
        var tile = OperaRooms.kpi(Optional.of(calendar), "verCupoOpera");
        assertThat(tile.value()).isEqualTo("2");
        assertThat(tile.unit()).isEqualTo("tipos sin cupo alguna noche");
        assertThat(tile.description()).isEqualTo("DBPB 16/11–17/11 · SJLB 17/11");
        assertThat(tile.actionId()).isEqualTo("verCupoOpera");
    }

    @Test
    void theGridHasARowPerTypeAndAColumnPerNight() throws Exception {
        var grid = OperaRooms.grid(OperaRooms.calendar(new ObjectMapper().readTree(ANSWER)));
        assertThat(grid.columns()).extracting(c -> c.label()).containsExactly("lun 16/11", "mar 17/11", "mié 18/11");
        var rows = grid.sections().getFirst().rows();
        assertThat(rows).extracting(r -> r.label()).containsExactly("DBPB", "SJLB", "STDK");
        assertThat(rows.get(0).cells()).extracting(c -> c.value() + ":" + c.tone()).containsExactly("0:danger", "0:danger", "1:warning");
        assertThat(rows.get(2).cells()).extracting(c -> c.value()).containsExactly("5+", "5+", "4");
    }

    @Test
    void unreadItSaysSo() {
        assertThat(OperaRooms.kpi(Optional.empty(), "x").value()).isEqualTo("—");
    }
}
