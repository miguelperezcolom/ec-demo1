package io.mateu.ecdemo1.crsintegration.source;

import java.util.List;

/** The CRS's catalog of codes, as its API returns it. */
public record CatalogView(List<Hotel> hotels, List<Code> ratePlans, List<Code> boards, List<Code> channels,
                          List<Code> cancellationReasons, List<Code> paymentMethods) {

    public record Hotel(String code, String name, List<Code> roomTypes) {
    }

    public record Code(String code, String name) {
    }
}
