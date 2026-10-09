package io.mateu.ecdemo1.frontoffice.infra.api;

import io.mateu.ecdemo1.frontoffice.application.DemoKnownCustomers;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /demo/known-customers?count=3}: makes a few of today's holders returning customers the
 * desk will recognise (see {@link DemoKnownCustomers}). Internal, as {@code /api/**}: called inside the
 * cluster by the demo's tooling, not routed from the internet.
 */
@RestController
public class DemoKnownCustomersApi {

  final DemoKnownCustomers known;

  public DemoKnownCustomersApi(DemoKnownCustomers known) {
    this.known = known;
  }

  @PostMapping("/demo/known-customers")
  public List<DemoKnownCustomers.Seeded> seed(@RequestParam(defaultValue = "3") int count) {
    return known.seed(count);
  }
}
