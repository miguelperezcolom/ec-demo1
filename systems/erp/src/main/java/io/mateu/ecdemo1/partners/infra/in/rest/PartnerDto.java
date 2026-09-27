package io.mateu.ecdemo1.partners.infra.in.rest;

import io.mateu.ecdemo1.partners.domain.partner.Address;
import io.mateu.ecdemo1.partners.domain.partner.BillingMode;
import io.mateu.ecdemo1.partners.domain.partner.Partner;
import io.mateu.ecdemo1.partners.domain.partner.PartnerType;

import java.time.Instant;

public record PartnerDto(String code, PartnerType type, String name, String taxId, Address address, String email,
                         String phone, BillingMode billingMode, boolean active, long version, Instant created,
                         Instant updated, String pmsProfileId, String pmsProfileType) {

    public static PartnerDto of(Partner p) {
        var d = p.getDetails();
        return new PartnerDto(p.getCode(), d.type(), d.name(), d.taxId(), d.address(), d.email(), d.phone(),
                d.billingMode(), p.isActive(), p.getVersion(), p.getCreated(), p.getUpdated(),
                p.getPmsProfile() == null ? null : p.getPmsProfile().profileId(),
                p.getPmsProfile() == null ? null : p.getPmsProfile().profileType());
    }
}
