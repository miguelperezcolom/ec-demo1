package io.mateu.ecdemo1.mapping.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.config.TolerantReader;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Ask the agent" with nothing chosen asks for every hotel with something unmapped, whatever its
 * integration: a hotel whose codes all resolve is not asked about, nor one whose integration is
 * decommissioned.
 */
class HotelsWithPendingTest {

    final IntegrationClients clients = new IntegrationClients(
            new MappingProperties(null, null, null, null, null, Duration.ofSeconds(1)), new TolerantReader(new ObjectMapper())) {
        @Override
        public List<IntegrationView> integrations() {
            return List.of(new IntegrationView("1", "MRU01", "XMAR", IntegrationStatus.ACTIVE),
                    new IntegrationView("2", "PMI01", "XPMI", IntegrationStatus.ACTIVE),
                    new IntegrationView("3", "OLD01", "XOLD", IntegrationStatus.DECOMMISSIONED));
        }

        @Override
        public List<CodeEntry> crsCatalog() {
            return List.of(new CodeEntry(CodeType.RATE_PLAN, "MRU01", "EMPLEADOS-27", "Empleados"),
                    new CodeEntry(CodeType.RATE_PLAN, "PMI01", "RACK", "Rack"),
                    new CodeEntry(CodeType.RATE_PLAN, "OLD01", "OLD", "Old"));
        }
    };

    /** Only PMI01's RACK has an equivalence. */
    final Dictionary dictionary = new Dictionary(null, null, null, null) {
        @Override
        public Optional<Translation> resolve(String hotelCode, CodeType type, String code) {
            return "RACK".equals(code) ? Optional.of(new Translation(type, code, "RACK", java.util.Map.of())) : Optional.empty();
        }
    };

    final MappingEntryRepository entries = (MappingEntryRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{MappingEntryRepository.class}, (proxy, method, args) -> List.of());

    @Test
    void everyHotelInServiceWithSomethingUnmappedAndNoOther() {
        assertThat(new Pending(clients, dictionary, entries).hotelsWithPending()).containsExactly("MRU01");
    }
}
