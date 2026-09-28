package io.mateu.ecdemo1.mdm.rest;

import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceUsage;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How much of Salesforce's daily allowance is spent, and how much of it was the MDM — for the
 * consoles' header, through integrations-service. Inside the cluster only: the gateway routes the
 * MDM's screens, not this. Numbers already known; asking costs the org no call.
 */
@RestController
@RequiredArgsConstructor
public class UsageController {

    final SalesforceUsage usage;

    @Operation(summary = "The org's API allowance and the MDM's own calls to Salesforce in the last 24 hours, by purpose")
    @GetMapping("/usage/salesforce")
    public ApiUsage salesforce() {
        return usage.usage();
    }
}
