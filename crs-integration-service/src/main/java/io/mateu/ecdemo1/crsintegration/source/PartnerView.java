package io.mateu.ecdemo1.crsintegration.source;

/** A partner as the master's API returns it. */
public record PartnerView(String code, String type, String name, String taxId, Address address, String email,
                          String phone, String billingMode, boolean active, long version, String pmsProfileId,
                          String pmsProfileType) {

    public record Address(String line, String city, String postalCode, String countryCode) {
    }
}
