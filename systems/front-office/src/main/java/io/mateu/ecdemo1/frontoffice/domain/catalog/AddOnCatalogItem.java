package io.mateu.ecdemo1.frontoffice.domain.catalog;

import java.math.BigDecimal;

/**
 * An add-on offered during check-in (packages, transfer, late check-out…). Reference data;
 * {@code includedLabel} marks items included for certain tiers ("Incluido Platinum") instead of
 * carrying a price.
 */
public record AddOnCatalogItem(
    String id,
    String icon,
    String title,
    String description,
    BigDecimal price,
    String unit,
    String includedLabel) {}
