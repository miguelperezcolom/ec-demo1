package io.mateu.ecdemo1.crsintegration.source;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A booking as the CRS's API returns it — Rumbo's model, and nobody past this package sees it. Only
 * the fields the integration reads; the reader ignores the rest.
 */
public record BookingView(String id, String hotelCode, String currency, String status, long version,
                          String channelCode, String partnerCode, String externalReference,
                          LocalDate arrival, LocalDate departure, Holder holder, List<Room> rooms,
                          List<Payment> payments, BigDecimal totalAmount, String comments,
                          Cancellation cancellation, PmsReference pmsReference, BigDecimal originalAmount) {

    public record Holder(String firstName, String lastName, String email, String phone, String nationality) {
    }

    public record Room(int line, String roomTypeCode, String ratePlanCode, String boardCode, int adults,
                       List<Integer> childrenAges, List<Guest> guests, List<NightlyRate> nightlyRates) {
    }

    public record Guest(String firstName, String lastName, String type, Integer age, LocalDate birthDate,
                        String nationality, String documentType, String documentNumber) {
    }

    public record NightlyRate(LocalDate date, BigDecimal amount) {
    }

    public record Payment(String paymentId, String type, String methodCode, BigDecimal amount, LocalDate date,
                          String reference) {
    }

    public record Cancellation(String reasonCode, BigDecimal fee, Integer feePercent) {
    }

    public record PmsReference(String reservationId) {
    }
}
