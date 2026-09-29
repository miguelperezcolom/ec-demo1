package io.mateu.ecdemo1.frontoffice.infra.pms;

import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.frontoffice.application.PmsStays;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Every example of front-office-commands is read the way the front office's listener reads it (its
 * Jackson 3 mapper) and taken by the stays: a stay written, a catalogue replaced.
 */
class FrontOfficeCommandsContractTest {

    @Test
    void everyExampleOfFrontOfficeCommandsIsTaken() {
        var stays = mock(PmsStays.class);

        var examples = Contracts.topic("front-office-commands").examples();
        for (var json : examples) {
            stays.take(FrontOfficeCommands.JSON.readValue(json.getBytes(), FrontOfficeCommand.class));
        }

        var taken = ArgumentCaptor.forClass(FrontOfficeCommand.class);
        verify(stays, times(examples.size())).take(taken.capture());
        assertThat(taken.getAllValues()).anySatisfy(c -> {
            assertThat(c).isInstanceOf(FrontOfficeCommand.WriteStay.class);
            var stay = (FrontOfficeCommand.WriteStay) c;
            assertThat(stay.crsLocator()).isEqualTo("12E45");
            assertThat(stay.checkIn()).isNotNull();
            assertThat(stay.total()).isNotNull();
        });
        assertThat(taken.getAllValues()).anySatisfy(c -> {
            assertThat(c).isInstanceOf(FrontOfficeCommand.ReplaceCatalogue.class);
            assertThat(((FrontOfficeCommand.ReplaceCatalogue) c).entries()).isNotEmpty();
        });
        assertThat(taken.getAllValues()).anySatisfy(c -> {
            assertThat(c).isInstanceOf(FrontOfficeCommand.RecordReception.class);
            var reception = (FrontOfficeCommand.RecordReception) c;
            assertThat(reception.operation()).isEqualTo(FrontOfficeCommand.ReceptionOperation.CHECK_OUT);
            assertThat(reception.invoice().number()).isEqualTo("XMAR377");
            assertThat(reception.invoice().date()).isNotNull();
            assertThat(reception.invoice().pdf()).isNotBlank();
        });
    }
}
