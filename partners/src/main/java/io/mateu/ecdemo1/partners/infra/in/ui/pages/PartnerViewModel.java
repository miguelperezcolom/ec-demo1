package io.mateu.ecdemo1.partners.infra.in.ui.pages;

import io.mateu.ecdemo1.partners.application.usecases.PartnerService;
import io.mateu.ecdemo1.partners.domain.partner.Address;
import io.mateu.ecdemo1.partners.domain.partner.BillingMode;
import io.mateu.ecdemo1.partners.domain.partner.Partner;
import io.mateu.ecdemo1.partners.domain.partner.PartnerDetails;
import io.mateu.ecdemo1.partners.domain.partner.PartnerType;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/** The partner form. See booking's BookingViewModel for why status starts non-null. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class PartnerViewModel implements Identifiable {

    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "New");

    @Section("Partner")
    @NotEmpty
    String code;
    @NotNull
    PartnerType type;
    @NotEmpty
    String name;
    @NotNull
    BillingMode billingMode;

    @Section("Tax and billing")
    String taxId;
    String addressLine;
    String city;
    String postalCode;
    String countryCode;
    String email;
    String phone;

    @Section("Tracking")
    @ReadOnly
    @HiddenInCreate
    Long version;
    /** Which profile it is in Opera: the integration creates it there only while this is empty. */
    @ReadOnly
    @HiddenInCreate
    String operaProfile;

    final PartnerService service;

    public String create(HttpRequest httpRequest) {
        return service.create(code, details());
    }

    public void save(HttpRequest httpRequest) {
        service.update(code, details());
    }

    @Toolbar
    @Action
    public Object resync(HttpRequest httpRequest) {
        service.resync(code);
        return new Message("Partner announced again: the integration will project it to the PMS");
    }

    private PartnerDetails details() {
        return new PartnerDetails(type, name, taxId, new Address(addressLine, city, postalCode, countryCode),
                email, phone, billingMode);
    }

    public PartnerViewModel load(Partner partner) {
        var d = partner.getDetails();
        status = partner.isActive() ? new Status(StatusType.SUCCESS, "Active") : new Status(StatusType.NONE, "Inactive");
        code = partner.getCode();
        type = d.type();
        name = d.name();
        billingMode = d.billingMode();
        taxId = d.taxId();
        addressLine = d.address() != null ? d.address().line() : null;
        city = d.address() != null ? d.address().city() : null;
        postalCode = d.address() != null ? d.address().postalCode() : null;
        countryCode = d.address() != null ? d.address().countryCode() : null;
        email = d.email();
        phone = d.phone();
        version = partner.getVersion();
        operaProfile = partner.getPmsProfile() == null ? "Not in Opera yet"
                : partner.getPmsProfile().profileId() + " · " + partner.getPmsProfile().profileType();
        return this;
    }

    @Override
    public String id() {
        return code;
    }

    @Override
    public String toString() {
        return code != null ? code + " · " + name : "New partner";
    }
}
