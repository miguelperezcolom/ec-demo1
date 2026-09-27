package io.mateu.ecdemo1.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.config.TolerantReader;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * What is left to map for a hotel. A hotel with codes of its own of a type (MRU01, its catalog
 * imported from its Opera property's) has those to map, not the chain's of that type.
 */
class PendingTest {

    static final List<CodeEntry> CRS = List.of(
            new CodeEntry(CodeType.HOTEL, null, "PMI01", "Riu Demo Palma"),
            new CodeEntry(CodeType.HOTEL, null, "MRU01", "Riu Demo Mauricio"),
            new CodeEntry(CodeType.ROOM_TYPE, "PMI01", "DBL", "Doble"),
            new CodeEntry(CodeType.ROOM_TYPE, "MRU01", "JS-SEA", "Junior suite vista mar"),
            new CodeEntry(CodeType.RATE_PLAN, null, "BAR", "Tarifa pública"),
            new CodeEntry(CodeType.BOARD, null, "MP", "Media pensión"),
            new CodeEntry(CodeType.PARTNER_TYPE, null, "TOUR_OPERATOR", "tour operator"),
            new CodeEntry(CodeType.RATE_PLAN, "MRU01", "DIRECTA", "Venta directa 2026"),
            new CodeEntry(CodeType.BOARD, "MRU01", "TODO-INCLUIDO", "Todo incluido"));

    final Pending pending = new Pending(
            new IntegrationClients(new MappingProperties(null, null, null, null, null, Duration.ofSeconds(1)),
                    new TolerantReader(new ObjectMapper())) {
                @Override
                public List<CodeEntry> crsCatalog() {
                    return CRS;
                }
            },
            new Dictionary(null, null, null, null) {
                @Override
                public Optional<Translation> resolve(String hotelCode, CodeType type, String code) {
                    return Optional.empty();
                }
            },
            (MappingEntryRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{MappingEntryRepository.class}, (proxy, method, args) -> List.of()));

    @Test
    void aHotelWithItsOwnCodesOfATypeHasThoseToMapAndNotTheChains() {
        assertThat(pending.pendingCodes("MRU01")).extracting(Pending.PendingCode::type, Pending.PendingCode::code)
                .containsExactlyInAnyOrder(
                        tuple(CodeType.HOTEL, "MRU01"),
                        tuple(CodeType.ROOM_TYPE, "JS-SEA"),
                        tuple(CodeType.PARTNER_TYPE, "TOUR_OPERATOR"),
                        tuple(CodeType.RATE_PLAN, "DIRECTA"),
                        tuple(CodeType.BOARD, "TODO-INCLUIDO"));
    }

    @Test
    void aHotelWithoutCodesOfItsOwnHasTheChains() {
        assertThat(pending.pendingCodes("PMI01")).extracting(Pending.PendingCode::type, Pending.PendingCode::code)
                .containsExactlyInAnyOrder(
                        tuple(CodeType.HOTEL, "PMI01"),
                        tuple(CodeType.ROOM_TYPE, "DBL"),
                        tuple(CodeType.RATE_PLAN, "BAR"),
                        tuple(CodeType.BOARD, "MP"),
                        tuple(CodeType.PARTNER_TYPE, "TOUR_OPERATOR"));
    }
}
