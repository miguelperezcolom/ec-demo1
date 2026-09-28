package io.mateu.ecdemo1.frontoffice.infra.mdm;

import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.integration.model.customer.CustomerEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every example of the customers topic is read the way the front office's listener reads it (its
 * Jackson 3 mapper) and reaches the kardex with the customer's data.
 */
class CustomerEventsContractTest {

    @Test
    void everyExampleOfCustomersReachesTheKardex() {
        var kardex = mock(Kardex.class);
        when(kardex.projected(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);

        var examples = Contracts.topic("customers").examples();
        for (var json : examples) {
            var event = CustomerEvents.JSON.readValue(json.getBytes(), CustomerEvent.class);
            assertThat(CustomerEvents.apply(kardex, event)).isTrue();
        }

        var ids = ArgumentCaptor.forClass(String.class);
        var updates = ArgumentCaptor.forClass(Kardex.Update.class);
        verify(kardex, times(examples.size())).projected(ids.capture(), updates.capture());
        assertThat(ids.getAllValues()).allSatisfy(id -> assertThat(id).isNotBlank());
        assertThat(updates.getAllValues()).allSatisfy(u -> assertThat(u.name()).isNotBlank());
    }
}
