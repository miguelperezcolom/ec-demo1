package io.mateu.ecdemo1.crsintegration.source;

import java.util.List;

/** The CRS's catalog of codes, as its API returns it. */
public record CatalogView(List<Hotel> hotels, List<Code> ratePlans, List<Code> boards, List<Code> channels,
                          List<Code> cancellationReasons, List<Code> paymentMethods) {

    /** {@code codes}: the hotel's own rate plans, boards, channels, reasons and methods; null, the chain's. */
    public record Hotel(String code, String name, List<Code> roomTypes, Codes codes) {
    }

    public record Codes(List<Code> ratePlans, List<Code> boards, List<Code> channels,
                        List<Code> cancellationReasons, List<Code> paymentMethods) {
    }

    public record Code(String code, String name) {
    }
}
