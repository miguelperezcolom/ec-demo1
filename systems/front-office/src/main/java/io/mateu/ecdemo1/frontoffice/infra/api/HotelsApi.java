package io.mateu.ecdemo1.frontoffice.infra.api;

import io.mateu.ecdemo1.frontoffice.ui.common.Hotels;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeHotel;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The hotels this front office serves, for whoever asks: the pms-fo integration is registered with one
 * of them, and with the PMS property it takes.
 */
@RestController
@RequestMapping("/api/hotels")
public class HotelsApi {

  final Hotels hotels;

  public HotelsApi(Hotels hotels) {
    this.hotels = hotels;
  }

  @GetMapping
  public List<FrontOfficeHotel> list() {
    return hotels.served().stream()
        .map(h -> new FrontOfficeHotel(h.code(), h.name(), h.pmsCode()))
        .toList();
  }
}
