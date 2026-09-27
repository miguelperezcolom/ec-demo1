package io.mateu.ecdemo1.booking.application.usecases.booking;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Payment;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** Money the central office has collected, as someone registers it. {@code date} defaults to today. */
public record PaymentRequest(PaymentType type, String methodCode, BigDecimal amount, LocalDate date,
                             String reference) {

    /** The payment, with a new id — its method checked against the hotel's, which may have its own. */
    public Payment toPayment(CrsCatalog catalog, String hotelCode, Clock clock) {
        var method = catalog.paymentMethod(hotelCode, methodCode);
        return new Payment(
                UUID.randomUUID().toString(),
                type,
                method.code(),
                amount,
                date != null ? date : LocalDate.now(clock),
                reference);
    }
}
