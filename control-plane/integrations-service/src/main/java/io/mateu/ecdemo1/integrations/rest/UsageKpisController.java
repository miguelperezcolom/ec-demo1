package io.mateu.ecdemo1.integrations.rest;

import io.mateu.ecdemo1.integrations.ui.usage.ApiUsageKpis;
import io.mateu.uidl.data.MetricCard;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The external APIs' KPI cards, for the consoles' welcome pages to lay out among their own tiles —
 * the shells ask it from inside the cluster when they render their home. Not routed by the gateway,
 * like the services' own {@code /usage}: only the screens under {@code /_api-usage} are.
 *
 * <p>A call costs Salesforce nothing: the numbers are the ones the MDM and the connector already
 * hold ({@link io.mateu.ecdemo1.integrations.usage.ApiUsages}, cached).
 */
@RestController
@RequiredArgsConstructor
public class UsageKpisController {

    final ApiUsageKpis kpis;

    @GetMapping("/usage/kpis")
    @Operation(summary = "The external APIs' KPI cards: Salesforce's calls left, our pace, Opera's calls today")
    public List<MetricCard> kpis() {
        return kpis.cards();
    }
}
