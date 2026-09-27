package io.mateu.ecdemo1.booking.application.usecases.booking.quote;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.NightlyRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a booking would cost if it were made now, priced exactly as creating it prices it. Nothing is
 * kept and nothing is held: the CRS does not model availability, so a quote is a price, not a room.
 */
public record Quote(String hotelCode,
                    String currency,
                    String channelCode,
                    LocalDate arrival,
                    LocalDate departure,
                    int nights,
                    List<QuotedRoom> rooms,
                    BigDecimal total) {

    public record QuotedRoom(int line,
                             String roomTypeCode,
                             String roomTypeName,
                             String ratePlanCode,
                             String ratePlanName,
                             String boardCode,
                             String boardName,
                             int adults,
                             List<Integer> childrenAges,
                             List<NightlyRate> nightlyRates,
                             BigDecimal total) {
    }
}
