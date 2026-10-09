package io.mateu.ecdemo1.loyalty.infra.in.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mateu.ecdemo1.loyalty.application.Loyalty;
import io.mateu.ecdemo1.loyalty.store.Tier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Riu Class over HTTP, inside the cluster only (the gateway does not route it): the front office asks a
 * guest's tier and points, by card number or by MDM customer code; the demo's seeding creates members.
 */
@RestController
@RequestMapping("/members")
public class MembersController {

    /**
     * What a PUT sends: everything optional but the customer — what is not sent is kept. Tolerant: the
     * application's mapper (Mateu's) refuses unknown fields, and a seeding script sending one more must
     * not fail.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemberRequest(String customerCode, String tier, Long points, LocalDate memberSince) {
    }

    final Loyalty loyalty;

    public MembersController(Loyalty loyalty) {
        this.loyalty = loyalty;
    }

    @GetMapping("/{memberNumber}")
    public ResponseEntity<MemberView> byNumber(@PathVariable String memberNumber) {
        return ResponseEntity.of(loyalty.find(memberNumber).map(MemberView::of));
    }

    @GetMapping
    public ResponseEntity<MemberView> byCustomer(@RequestParam String customerCode) {
        return ResponseEntity.of(loyalty.findByCustomer(customerCode).map(MemberView::of));
    }

    @PutMapping("/{memberNumber}")
    public MemberView put(@PathVariable String memberNumber, @RequestBody MemberRequest request) {
        return MemberView.of(loyalty.upsert(memberNumber, new Loyalty.MemberUpdate(request.customerCode(),
                Tier.parse(request.tier()), request.points(), request.memberSince())));
    }

    /** A request that makes no sense (no customer, an unknown tier, negative points): 400 and why. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
