package io.mateu.ecdemo1.frontoffice.ui.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.interfaces.HttpRequest;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;

class HotelsTest {

  final Hotels hotels = new Hotels("MRU01", "XMAR", "Riu Demo Mauricio");

  @Test
  void theOptionsAreTheHotelThisFrontOfficeServes() {
    var options = hotels.search("hotel", "", new Pageable(0, 100, List.of()), null).page().content();
    assertThat(options).hasSize(1);
    assertThat(options.get(0).value()).isEqualTo("MRU01");
    assertThat(options.get(0).label()).isEqualTo("Riu Demo Mauricio · MRU01");
  }

  @Test
  void noChoiceOrAnUnknownOneIsTheFirstHotel() {
    assertThat(hotels.selected(null).code()).isEqualTo("MRU01");
    assertThat(hotels.selected(choosing(null)).code()).isEqualTo("MRU01");
    // what the old hard-coded selector left in the browser
    assertThat(hotels.selected(choosing("PuntaCana")).code()).isEqualTo("MRU01");
    assertThat(hotels.selected(choosing("MRU01")).pmsCode()).isEqualTo("XMAR");
  }

  @Test
  void theStaysHereAreTheConfiguredHotels() {
    assertThat(hotels.holdsStaysOfSelected(choosing("MRU01"))).isTrue();
    assertThat(hotels.holdsStays(new Hotels.Hotel("PMI01", "XPMI", "Otro"))).isFalse();
  }

  @Test
  void aHotelWithoutNameShowsItsCode() {
    assertThat(new Hotels("MRU01", "XMAR", "").selected(null).label()).isEqualTo("MRU01");
  }

  static HttpRequest choosing(String hotel) {
    return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(), new Class<?>[] {HttpRequest.class},
        (proxy, method, args) -> {
          if (method.getName().equals("appContext")) {
            return hotel;
          }
          if (method.isDefault()) {
            return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
          }
          return null;
        });
  }
}
