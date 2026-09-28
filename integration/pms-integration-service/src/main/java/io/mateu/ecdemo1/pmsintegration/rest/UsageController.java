package io.mateu.ecdemo1.pmsintegration.rest;

import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How much of Opera's API this connector spent in the last 24 hours, by what for — for the consoles'
 * header, through integrations-service. Inside the cluster only, like the rest of this API.
 */
@RestController
@RequiredArgsConstructor
public class UsageController {

    final OperaUsage usage;

    @GetMapping("/usage/opera")
    public ApiUsage opera() {
        return usage.usage();
    }
}
