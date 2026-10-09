package io.mateu.ecdemo1.customerhistory.infra.in.rest;

import io.mateu.ecdemo1.customerhistory.application.CustomerHistory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The demo's past stays: a customer of the demo has years of history before the stay closed in front
 * of the audience. Seeded the same every time (source DEMO), and taken away without touching the
 * stays the front office closed.
 */
@RestController
@RequestMapping("/demo/stays")
public class DemoStaysController {

    /** @param count how many past stays: 4 if missing, between 1 and 8 */
    public record SeedRequest(String customerId, Integer count) {
    }

    final CustomerHistory history;

    public DemoStaysController(CustomerHistory history) {
        this.history = history;
    }

    @PostMapping
    public CustomerHistory.Summary seed(@RequestBody SeedRequest request) {
        return history.seedDemo(request.customerId(), request.count());
    }

    @DeleteMapping("/{customerId}")
    public Map<String, Integer> delete(@PathVariable String customerId) {
        return Map.of("deleted", history.deleteDemo(customerId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail refused(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }
}
