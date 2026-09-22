package io.mateu.ecdemo1.partners.domain.partner;

/** Everything about a partner an update replaces as a whole. */
public record PartnerDetails(PartnerType type, String name, String taxId, Address address, String email,
                             String phone, BillingMode billingMode) {

    public PartnerDetails {
        if (type == null || name == null || name.isBlank() || billingMode == null) {
            throw new IllegalArgumentException("A partner needs a type, a name and a billing mode");
        }
    }
}
