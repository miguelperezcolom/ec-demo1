package io.mateu.ecdemo1.frontoffice.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The per-request identity map: one load per aggregate in a request, and a save is never hidden. */
@SpringBootTest
class RequestCacheTest {

  @Autowired StayRepository stays;

  @AfterEach
  void endRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  /** A request as the filter leaves it. */
  private static void startRequest() {
    var request = new MockHttpServletRequest();
    request.setAttribute(RequestCache.ENABLED, Boolean.TRUE);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @Test
  void withinARequestTheSameAggregateIsLoadedOnce() {
    startRequest();
    var first = stays.findById("st-sophie").orElseThrow();
    assertSame(first, stays.findById("st-sophie").orElseThrow());
  }

  @Test
  void aSaveInTheRequestIsSeenByTheNextRead() {
    startRequest();
    var before = stays.findById("st-sophie").orElseThrow();
    stays.save(before.addAddOn("request-cache-test"));
    var after = stays.findById("st-sophie").orElseThrow();
    assertNotSame(before, after);
    assertEquals(before.addOns().size() + 1, after.addOns().size());
    stays.save(before);
  }

  @Test
  void outsideARequestNothingIsCached() {
    assertNotSame(stays.findById("st-sophie").orElseThrow(), stays.findById("st-sophie").orElseThrow());
  }
}
