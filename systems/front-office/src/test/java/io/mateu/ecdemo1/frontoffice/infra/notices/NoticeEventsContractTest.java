package io.mateu.ecdemo1.frontoffice.infra.notices;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged;
import org.junit.jupiter.api.Test;

/**
 * Every example of the notices topic is read the way the front office's listener reads it (its
 * Jackson 3 mapper), whole: a reservation's, a partner's and a customer's.
 */
class NoticeEventsContractTest {

  @Test
  void everyExampleOfNoticesIsReadWhole() {
    var examples = Contracts.topic("notices").examples();
    assertThat(examples).isNotEmpty();
    var read = examples.stream().map(json -> NoticeEvents.read(json.getBytes())).toList();

    assertThat(read).allSatisfy(e -> {
      assertThat(e).isNotNull();
      assertThat(e.noticeId()).isNotBlank();
      assertThat(e.subjectType()).isNotNull();
      assertThat(e.subjectId()).isNotBlank();
      assertThat(e.type()).isNotNull();
      assertThat(e.moments()).isNotEmpty();
    });
    assertThat(read).extracting(NoticeChanged::subjectType).contains(NoticeChanged.SubjectType.CUSTOMER,
        NoticeChanged.SubjectType.RESERVATION, NoticeChanged.SubjectType.PARTNER);
    assertThat(read).anySatisfy(e -> assertThat(e.subjectName()).isNotBlank());
    assertThat(read).anySatisfy(e -> assertThat(e.hotelCode()).isNotBlank());
  }
}
