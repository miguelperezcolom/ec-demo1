package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.StatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A workflow button answers with the state it left, so the form's badge follows, and keeps what is typed and unsaved. */
class ItemFormStateTest {

    final ItemRepository items = mock(ItemRepository.class);
    final Item item = mock(Item.class);
    final ItemForm form = new ItemForm(mock(ItemCommands.class), items, null, null, null, mock(Readers.class),
            null, null, null, null, null, null);

    @BeforeEach
    void anItem() {
        form.id = "i1";
        form.estado = new io.mateu.uidl.data.Status(StatusType.NONE, "Borrador");
        when(items.find(any())).thenReturn(Optional.of(item));
        when(item.latestVersion()).thenReturn(Optional.empty());
    }

    void itIsNow(ItemStatus status) {
        when(item.status()).thenReturn(status);
    }

    @Test
    void publishingShowsPublished() {
        itIsNow(ItemStatus.PUBLISHED);

        assertThat(form.publicar(null)).isInstanceOfSatisfying(List.class, answer -> {
            assertThat(answer.get(0)).isSameAs(form);
            assertThat(answer.get(1)).isInstanceOf(Message.class);
        });
        assertThat(form.estado.type()).isEqualTo(StatusType.SUCCESS);
        assertThat(form.estado.message()).isEqualTo(ItemStatus.PUBLISHED.label());
    }

    @Test
    void retiringShowsRetired() {
        itIsNow(ItemStatus.RETIRED);

        form.retirar(null);

        assertThat(form.estado.type()).isEqualTo(StatusType.DANGER);
    }

    @Test
    void sendingToReviewAndRejectingShowTheirStates() {
        itIsNow(ItemStatus.IN_REVIEW);
        form.enviarARevision(null);
        assertThat(form.estado.type()).isEqualTo(StatusType.WARNING);

        itIsNow(ItemStatus.DRAFT);
        form.rechazar(null);
        assertThat(form.estado.type()).isEqualTo(StatusType.NONE);
    }

    @Test
    void whatIsTypedAndNotSavedStays() {
        itIsNow(ItemStatus.PUBLISHED);
        form.titleEs = "Escrito sin guardar";

        form.publicar(null);

        assertThat(form.titleEs).isEqualTo("Escrito sin guardar");
    }
}
