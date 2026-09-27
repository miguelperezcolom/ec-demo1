package io.mateu.ecdemo1.communication.application;

import io.mateu.ecdemo1.communication.store.Channel;
import io.mateu.ecdemo1.communication.store.Notification;
import io.mateu.ecdemo1.communication.store.NotificationRepository;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The query and application services the communication screens go through. */
class CommunicationQueriesTest {

    final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    final NotificationQueries notifications = new NotificationQueries(notificationRepository);
    final RecipientRepository recipientRepository = mock(RecipientRepository.class);
    final RecipientQueries recipientQueries = new RecipientQueries(recipientRepository);
    final Recipients recipients = new Recipients(recipientRepository);

    @SuppressWarnings("unchecked")
    Pageable notificationsPageAsked(Pageable pageable) {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(notificationRepository.findAll(any(Specification.class), captor.capture())).thenReturn(Page.empty());
        notifications.find("x", pageable);
        return captor.getValue();
    }

    @Test
    void notificationsAreReadAPageAtATimeNewestFirstUnlessAnotherOrderIsAsked() {
        var asked = notificationsPageAsked(PageRequest.of(2, 20));
        assertThat(asked.getPageNumber()).isEqualTo(2);
        assertThat(asked.getPageSize()).isEqualTo(20);
        assertThat(asked.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "requestedAt"));
        assertThat(notificationsPageAsked(PageRequest.of(0, 20, Sort.by("hotelCode"))).getSort())
                .isEqualTo(Sort.by("hotelCode"));
    }

    @SuppressWarnings("unchecked")
    static CriteriaBuilder where(String text, Root<Notification> root) {
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        NotificationQueries.matching(text).toPredicate(root, mock(CriteriaQuery.class), cb);
        return cb;
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTextLooksInTitleAndHotelAndTheTypesItNames() {
        Root<Notification> root = mock(Root.class, RETURNS_DEEP_STUBS);
        var cb = where(" Rejected ", root);
        verify(cb, org.mockito.Mockito.times(2)).like(any(), eq("%rejected%"));
        verify(root.get("type")).in(List.of(NotificationType.PMS_REJECTED));
    }

    @Test
    @SuppressWarnings("unchecked")
    void noTextIsEveryNotification() {
        Root<Notification> root = mock(Root.class, RETURNS_DEEP_STUBS);
        var cb = where("  ", root);
        verify(cb).conjunction();
        verify(cb, never()).like(any(), any(String.class));
    }

    @Test
    void aTextThatNamesNoTypeLooksOnlyInTitleAndHotel() {
        assertThat(NotificationQueries.typesNamedBy("mru01")).isEmpty();
        assertThat(NotificationQueries.typesNamedBy("_")).contains(NotificationType.CAUSE_OPENED, NotificationType.PMS_REJECTED);
    }

    @Test
    void recipientsAreReadAPageAtATimeByName() {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(recipientRepository.findAll(captor.capture())).thenReturn(Page.empty());
        recipientQueries.page(PageRequest.of(1, 20));
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("name", "id"));
    }

    @Test
    void aRecipientIsSavedAsItShouldBe() {
        when(recipientRepository.findById("r1")).thenReturn(Optional.of(new Recipient()));
        when(recipientRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var id = recipients.save(new Recipients.RecipientChange("r1", "Front desk", true, "ana", null, null,
                List.of(NotificationType.PMS_REJECTED), true, "MRU01", EnumSet.of(Channel.INBOX), null));
        assertThat(id).isEqualTo("r1");
        var saved = ArgumentCaptor.forClass(Recipient.class);
        verify(recipientRepository).save(saved.capture());
        assertThat(saved.getValue().types).isEqualTo("PMS_REJECTED");
        assertThat(saved.getValue().channels).isEqualTo("INBOX");
        assertThat(saved.getValue().hotelCode).isEqualTo("MRU01");
        assertThat(saved.getValue().tasks).isTrue();
    }

    @Test
    void aRecipientThatCouldReachNobodyIsRefused() {
        assertThatThrownBy(() -> recipients.save(new Recipients.RecipientChange(null, "Nobody", true, null, null, null,
                List.of(), false, null, Set.of(Channel.INBOX, Channel.EMAIL), null)))
                .hasMessageContaining("Inbox and Web Push need users or roles.")
                .hasMessageContaining("E-mail needs an address.");
        verify(recipientRepository, never()).save(any());
    }
}
