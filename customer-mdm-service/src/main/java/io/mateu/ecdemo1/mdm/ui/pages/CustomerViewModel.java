package io.mateu.ecdemo1.mdm.ui.pages;

import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.interfaces.Identifiable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * One golden record, with where it came from. Read-only: the MDM is changed by reservations and by
 * the merges stewards make in Salesforce, not by hand here.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class CustomerViewModel implements Identifiable {

    @ReadOnly
    Status status;

    @Section("Identity")
    @ReadOnly
    String id;
    @ReadOnly
    String firstName;
    @ReadOnly
    String lastName;
    @ReadOnly
    String email;
    @ReadOnly
    String phone;
    @ReadOnly
    String nationality;
    @ReadOnly
    String birthDate;
    @ReadOnly
    String document;

    @Section("Salesforce")
    @ReadOnly
    String salesforce;
    @ReadOnly
    String contact;
    @ReadOnly
    String projectionError;

    @Section("Lineage")
    @ReadOnly
    String aliases;
    @ReadOnly
    @Stereotype(FieldStereotype.textarea)
    String survivorship;
    @ReadOnly
    @Stereotype(FieldStereotype.grid)
    List<SourceRow> reservations;

    final CustomerRepository customers;
    final SourceRepository sources;

    public CustomerViewModel load(Customer c) {
        status = CustomersPage.status(c);
        id = c.id;
        firstName = c.firstName;
        lastName = c.lastName;
        email = c.email;
        phone = c.phone;
        nationality = c.nationality;
        birthDate = c.birthDate == null ? "" : c.birthDate.toString();
        document = c.documentNumber == null ? "" : c.documentType + " " + c.documentNumber;
        salesforce = "%s · v%d".formatted(c.salesforceState, c.version);
        contact = c.salesforceContactId == null ? "" : c.salesforceContactId;
        projectionError = c.projectionError == null ? "" : c.projectionError;
        aliases = String.join(", ", customers.findByAliasOf(c.id).stream().map(a -> a.id).toList());
        survivorship = c.survivorship == null ? "" : c.survivorship;
        reservations = sources.findByCustomerIdOrderByFirstSeenAsc(c.id).stream()
                .map(s -> new SourceRow(s.hotelCode + "/" + s.locator, s.passenger, s.firstCustomerId, String.valueOf(s.firstSeen)))
                .toList();
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return (firstName == null ? "" : firstName + " ") + (lastName == null ? "" : lastName) + " · " + id;
    }
}
