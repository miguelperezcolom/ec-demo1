package io.mateu.ecdemo1.frontoffice.infra.mdm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.frontoffice.application.GuestNotices;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Every example of the customer-notices topic is read the way the front office's listener reads it
 * (its Jackson 3 mapper) and reaches the desk's notices whole.
 */
class CustomerNoticeEventsContractTest {

  @Test
  void everyExampleOfCustomerNoticesIsTaken() {
    var notices = mock(GuestNotices.class);

    var examples = Contracts.topic("customer-notices").examples();
    for (var json : examples) {
      var event = CustomerNoticeEvents.read(json.getBytes());
      assertThat(event).as("readable: " + json).isNotNull();
      notices.take(event);
    }

    var taken = ArgumentCaptor.forClass(CustomerNoticeChanged.class);
    verify(notices, times(examples.size())).take(taken.capture());
    assertThat(taken.getAllValues()).allSatisfy(e -> {
      assertThat(e.noticeId()).isNotBlank();
      assertThat(e.customerId()).isNotBlank();
      assertThat(e.type()).isNotNull();
      assertThat(e.showAt()).isNotEmpty();
    });
    assertThat(taken.getAllValues()).anySatisfy(e -> assertThat(e.from()).isNotNull());
  }
}
