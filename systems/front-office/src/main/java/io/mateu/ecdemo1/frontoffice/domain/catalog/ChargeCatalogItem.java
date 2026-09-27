package io.mateu.ecdemo1.frontoffice.domain.catalog;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;

/** An item of the charge catalog used when posting charges to a folio. Reference data. */
public record ChargeCatalogItem(@Id String code, String name, BigDecimal price) {}
