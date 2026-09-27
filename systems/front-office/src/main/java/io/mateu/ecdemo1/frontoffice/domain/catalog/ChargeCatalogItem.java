package io.mateu.ecdemo1.frontoffice.domain.catalog;

import java.math.BigDecimal;

/** An item of the charge catalog used when posting charges to a folio. Reference data. */
public record ChargeCatalogItem(String code, String name, BigDecimal price) {}
