package io.mateu.ecdemo1.mdm.rest;

import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.customer.ResolvedIdentity;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@RestController
@RequiredArgsConstructor
public class CustomerController {

    final IdentityResolution resolution;
    final CustomerRepository customers;
    final SourceRepository sources;

    @PostMapping("/identities/resolve")
    @Operation(summary = "The customers a reservation's passengers are: existing ones when certain, provisional ones otherwise. Idempotent per reservation and passenger")
    public List<ResolvedIdentity> resolve(@RequestBody IdentityRequest request) {
        return resolution.resolve(request);
    }

    @GetMapping("/customers/{id}")
    @Operation(summary = "A customer by any code it ever had; an absorbed code answers with its survivor")
    public CustomerView customer(@PathVariable String id) {
        return view(resolution.survivorOf(id), id);
    }

    @GetMapping("/customers")
    @Operation(summary = "Customers whose name, email, document or code contains the text; merged ones are left out")
    public List<CustomerView> search(@RequestParam(defaultValue = "") String q) {
        var text = q.toLowerCase(Locale.ROOT);
        return customers.findAllByOrderByUpdatedAtDesc().stream()
                .filter(c -> c.aliasOf == null)
                .filter(c -> text.isEmpty() || (c.id + " " + c.fullName() + " " + c.email + " " + c.documentNumber)
                        .toLowerCase(Locale.ROOT).contains(text))
                .limit(100)
                .map(c -> view(c, c.id))
                .toList();
    }

    public CustomerView view(Customer c, String requestedId) {
        var aliases = customers.findByAliasOf(c.id).stream().map(a -> a.id).toList();
        var reservations = sources.findByCustomerIdOrderByFirstSeenAsc(c.id).stream()
                .map(s -> s.hotelCode + "/" + s.locator).distinct().toList();
        return CustomerView.of(c, requestedId, aliases, reservations);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NoSuchElementException e) {
        return e.getMessage();
    }
}
