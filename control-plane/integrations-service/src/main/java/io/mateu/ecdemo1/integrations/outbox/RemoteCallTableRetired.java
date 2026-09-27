package io.mateu.ecdemo1.integrations.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * The commands to other services went, for a while, through an HTTP outbox of their own — the
 * {@code remote_call} table and a relay. They go through the Kafka outbox now ({@link Commands}), and
 * the table goes: whatever it still held unsent is written to the Kafka outbox first, in the same
 * transaction that drops it, so no command asked for before the change is lost.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RemoteCallTableRetired {

    final JdbcTemplate jdbc;
    final Commands commands;
    final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void retire() {
        var exists = jdbc.queryForObject("select to_regclass('remote_call') is not null", Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            return;
        }
        var moved = 0;
        for (var row : jdbc.queryForList("""
                select kind, payload from remote_call where done_at is null and failed_at is null order by id""")) {
            if (carryOver((String) row.get("kind"), (String) row.get("payload"))) {
                moved++;
            }
        }
        jdbc.execute("drop table remote_call");
        log.info("remote_call retired: {} unsent command(s) carried over to the Kafka outbox", moved);
    }

    boolean carryOver(String kind, String payload) {
        JsonNode c;
        try {
            c = objectMapper.readTree(payload);
        } catch (IOException e) {
            log.warn("Unreadable remote call {} left behind: {}", kind, payload);
            return false;
        }
        switch (kind) {
            case "DefineHotel" -> commands.defineHotel(c.path("crsHotelCode").asText(), c.path("pmsHotelCode").asText(),
                    c.path("by").asText());
            case "DefinePartnerTypes" -> commands.definePartnerTypes(c.path("by").asText());
            case "RequestAgentProposal" -> commands.requestAgentProposal(c.path("crsHotelCode").asText());
            case "ResyncPartner" -> commands.resyncPartner(c.path("partnerCode").asText());
            case "ResolveCause" -> commands.resolveCauseIfOpen(c.path("causeKey").asText(), c.path("by").asText());
            default -> {
                log.warn("Remote call of unknown kind {} left behind: {}", kind, payload);
                return false;
            }
        }
        return true;
    }
}
