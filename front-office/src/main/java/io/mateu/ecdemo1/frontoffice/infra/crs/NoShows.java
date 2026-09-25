package io.mateu.ecdemo1.frontoffice.infra.crs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * The hotel tells the chain a reservation's guests did not arrive (HLA F006). What that costs is the
 * CRS's to decide: it cancels the booking as a no-show with its fee, and the result comes back here —
 * the stay as a no-show, costing the fee — and to Opera.
 */
@Slf4j
@Service
public class NoShows {

  private static NoShows instance;

  final RestClient crs;
  final String hotel;

  public NoShows(@Value("${frontoffice.crs-integration-url:}") String crsIntegrationUrl,
                 @Value("${frontoffice.hotel:MRU01}") String hotel) {
    this.crs = crsIntegrationUrl == null || crsIntegrationUrl.isBlank() ? null
        : RestClient.builder().baseUrl(crsIntegrationUrl).build();
    this.hotel = hotel;
    instance = this;
  }

  /** Reports the stay as a no-show; what to tell the desk. */
  public static String report(String stayId) {
    return instance == null ? "No se ha podido avisar al CRS." : instance.send(stayId);
  }

  String send(String stayId) {
    if (crs == null) {
      return "Este front office no está conectado al CRS: el no show queda solo aquí.";
    }
    try {
      crs.post().uri("/no-shows").body(Map.of("hotelCode", hotel, "locator", stayId, "reportedBy", "front office " + hotel))
          .retrieve().toBodilessEntity();
      log.info("{}: reported to the CRS as a no-show", stayId);
      return "Se comunica al CRS, que la cancela con su cargo de no show.";
    } catch (HttpClientErrorException.NotFound e) {
      return "Esta reserva no viene del CRS: el no show queda solo aquí.";
    } catch (HttpClientErrorException.Conflict e) {
      return "El CRS ya la tenía cancelada.";
    } catch (RuntimeException e) {
      log.warn("{}: the CRS did not take the no-show ({})", stayId, e.getMessage());
      return "El CRS no ha respondido: vuelve a marcarlo en un momento.";
    }
  }
}
