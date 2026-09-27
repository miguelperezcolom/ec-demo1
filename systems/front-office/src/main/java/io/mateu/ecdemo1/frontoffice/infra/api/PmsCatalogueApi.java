package io.mateu.ecdemo1.frontoffice.infra.api;

import io.mateu.ecdemo1.frontoffice.application.PmsStays;
import io.mateu.ecdemo1.frontoffice.infra.pms.PmsCatalogue;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCatalogueSummary;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueEntry;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.CatalogueType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The PMS's catalogue this front office reads its stays with, for whoever asks: the pms-fo
 * integration's catalogue gate opens on the summary.
 */
@RestController
@RequestMapping("/api/pms-catalogue")
public class PmsCatalogueApi {

  final PmsCatalogue catalogue;
  final PmsStays stays;

  public PmsCatalogueApi(PmsCatalogue catalogue, PmsStays stays) {
    this.catalogue = catalogue;
    this.stays = stays;
  }

  @GetMapping("/summary")
  public FrontOfficeCatalogueSummary summary() {
    return catalogue.summary(stays.pmsHotel());
  }

  @GetMapping
  public List<CatalogueEntry> list(@RequestParam(required = false) CatalogueType type) {
    return catalogue.list(stays.pmsHotel(), type);
  }
}
