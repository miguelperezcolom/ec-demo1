package io.mateu.ecdemo1.loyalty.infra.in.rest;

import io.mateu.ecdemo1.loyalty.store.Accrual;

import java.time.Instant;

/** The points a stay earned a member. */
public record AccrualView(String memberNumber, String stayId, String hotelCode, int nights, long points, Instant at) {

    public static AccrualView of(Accrual a) {
        return new AccrualView(a.memberNumber, a.stayId, a.hotelCode, a.nights, a.points, a.at);
    }
}
