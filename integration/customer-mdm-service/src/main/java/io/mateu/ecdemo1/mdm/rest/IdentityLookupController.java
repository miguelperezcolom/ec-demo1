package io.mateu.ecdemo1.mdm.rest;

import io.mateu.ecdemo1.mdm.application.IdentityLookup;
import io.mateu.ecdemo1.mdm.documents.DocumentDesk;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;

/** Who a guest at the desk is, or may be; and a document added to a customer. */
@RestController
@RequiredArgsConstructor
public class IdentityLookupController {

    final IdentityLookup lookup;
    final DocumentDesk desk;

    /**
     * The customer a document, an email or a Riu Class number is — exactly one of them. 200 with the
     * customer (its survivor, if it was merged); 404 when nobody; 409 when more than one customer holds it.
     *
     * <p>Why 409 and not the list: whoever asks is a desk with a guest in front of it. Answering with
     * several customers would show it other people's data — names, birth dates — to pick from, which is
     * exactly what data protection (GDPR, minimisation) forbids: the desk is told only that the key is
     * ambiguous and how many share it, and asks the guest for something else (their birth date, through
     * {@link #candidates}, or another document). Settling the duplicates is cleaning's, in Salesforce.
     */
    @GetMapping("/identities/lookup")
    @Operation(summary = "The customer a document (with an optional issuing country), an email or a Riu Class number is: "
            + "exactly one of them. 200 the customer; 404 nobody; 409 more than one, without their data")
    public ResponseEntity<?> lookup(@RequestParam(required = false) String documentNumber,
                                    @RequestParam(required = false) String country,
                                    @RequestParam(required = false) String email,
                                    @RequestParam(required = false) String riuClass) {
        return switch (lookup.lookup(documentNumber, country, email, riuClass)) {
            case IdentityLookup.Found found -> ResponseEntity.ok(found);
            case IdentityLookup.Ambiguous ambiguous -> ResponseEntity.status(HttpStatus.CONFLICT).body(ambiguous);
            case IdentityLookup.NotFound ignored -> ResponseEntity.notFound().build();
        };
    }

    @GetMapping("/identities/candidates")
    @Operation(summary = "Customers the guest may be: the same name and birth date (mandatory: without it, none), "
            + "the same nationality first; at most 5. Never merges anything")
    public List<IdentityLookup.Candidate> candidates(@RequestParam(required = false) String firstName,
                                                     @RequestParam(required = false) String lastName,
                                                     @RequestParam(required = false) String birthDate,
                                                     @RequestParam(required = false) String nationality) {
        if (birthDate == null || birthDate.isBlank()) {
            return List.of();
        }
        LocalDate born;
        try {
            born = LocalDate.parse(birthDate.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("birthDate is an ISO date, e.g. 1984-03-02");
        }
        return lookup.candidates(firstName, lastName, born, nationality);
    }

    @PostMapping("/customers/{id}/documents")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Add an identity document to a customer (its survivor); the main one too if it had none")
    public void addDocument(@PathVariable String id, @RequestBody DocumentDesk.NewDocument document) {
        desk.add(id, document);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NoSuchElementException e) {
        return e.getMessage();
    }
}
