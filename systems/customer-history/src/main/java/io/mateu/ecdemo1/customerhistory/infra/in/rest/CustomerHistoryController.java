package io.mateu.ecdemo1.customerhistory.infra.in.rest;

import io.mateu.ecdemo1.customerhistory.application.CustomerHistory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A customer's history, by any of their codes — an absorbed one answers as its survivor. Inside the
 * cluster only: the gateway routes the service's screens (/_history), not this. Never a 404: a customer
 * with no stays has a history of zeros, which is what a caller showing it wants anyway.
 */
@RestController
@RequestMapping("/customers")
public class CustomerHistoryController {

    final CustomerHistory history;

    public CustomerHistoryController(CustomerHistory history) {
        this.history = history;
    }

    @GetMapping("/{code}/summary")
    public CustomerHistory.Summary summary(@PathVariable String code) {
        return history.summary(code);
    }

    @GetMapping("/{code}/stays")
    public CustomerHistory.StayPage stays(@PathVariable String code, @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return history.stays(code, page, size);
    }
}
