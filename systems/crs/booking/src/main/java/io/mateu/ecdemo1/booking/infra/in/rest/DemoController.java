package io.mateu.ecdemo1.booking.infra.in.rest;

import io.mateu.ecdemo1.booking.application.usecases.intake.CrsIntake;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the control plane's Demo page reads of the CRS: whether it admits bookings now. Pausing and
 * resuming is the engine's (reset-demo's pause-intake and resume-intake), not a REST call's.
 */
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    final CrsIntake intake;

    @GetMapping("/intake")
    public CrsIntake.Status intake() {
        return intake.status();
    }
}
