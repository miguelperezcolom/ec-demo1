package io.mateu.ecdemo1.integration.model.reservation;

import java.util.List;

public record Room(int line,
                   String roomTypeCode,
                   String ratePlanCode,
                   String boardCode,
                   int adults,
                   List<Integer> childrenAges,
                   List<Person> guests,
                   List<NightlyRate> nightlyRates) {
}
