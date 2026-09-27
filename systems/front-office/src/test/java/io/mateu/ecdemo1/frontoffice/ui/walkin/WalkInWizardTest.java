package io.mateu.ecdemo1.frontoffice.ui.walkin;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.UICommand;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The walk-in, step by step: the stay, the CRS's price asked on entering its step (and why not, when
 * the CRS will not sell it), the holder, and the confirmation that opens the stay. The CRS's adapter
 * is a small server that quotes 2 nights at 412.00 and books, or refuses to quote when told.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:walk-in-wizard;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "frontoffice.walk-in-resend=1h"})
class WalkInWizardTest {

  static final AtomicBoolean refuseQuote = new AtomicBoolean();
  static HttpServer crs;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws IOException {
    crs = HttpServer.create(new InetSocketAddress(0), 0);
    crs.createContext("/", exchange -> {
      var path = exchange.getRequestURI().getPath();
      exchange.getRequestBody().readAllBytes();
      int code = 200;
      String answer;
      if (path.equals("/walk-ins/offer")) {
        answer = """
            {"hotelCode":"MRU01","hotelName":"Riu","roomTypes":[{"code":"STD-KING","name":"Estándar king"}],
             "ratePlans":[{"code":"DIRECTA","name":"Directa"}],"boards":[{"code":"DESAYUNO","name":"Desayuno"}]}""";
      } else if (path.equals("/walk-ins/quote")) {
        code = refuseQuote.get() ? 422 : 200;
        answer = refuseQuote.get() ? "{\"status\":422,\"detail\":\"No availability for STD-KING.\"}"
            : "{\"currency\":\"EUR\",\"nights\":2,\"total\":412.00}";
      } else {
        code = 201;
        answer = "{\"locator\":\"CRS777\"}";
      }
      var bytes = answer.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", code == 422 ? "application/problem+json" : "application/json");
      exchange.sendResponseHeaders(code, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    crs.start();
    registry.add("frontoffice.crs-integration-url", () -> "http://localhost:" + crs.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    crs.stop(0);
  }

  @Autowired ObjectProvider<WalkInWizard> wizards;
  @Autowired StayRepository stays;

  WalkInWizard wizard() {
    var wizard = wizards.getObject();
    wizard.estancia = new EstanciaWalkIn();
    wizard.estancia.setLlegada(LocalDate.now());
    wizard.estancia.setSalida(LocalDate.now().plusDays(2));
    wizard.estancia.setHabitacion("STD-KING");
    wizard.estancia.setTarifa("DIRECTA");
    wizard.estancia.setRegimen("DESAYUNO");
    return wizard;
  }

  @Test
  void theStayStepSaysWhatIsMissing() {
    var wizard = wizard();
    wizard.estancia.setRegimen(null);
    assertThat(wizard.problemaAlSalir("estancia", null)).isEqualTo("Falta de la estancia: régimen.");
    wizard.estancia.setRegimen("DESAYUNO");
    wizard.estancia.setSalida(LocalDate.now());
    assertThat(wizard.problemaAlSalir("estancia", null)).isEqualTo("La salida tiene que ser después de la llegada.");
  }

  @Test
  void thePriceIsTheCrssAndAConfirmedWalkInIsAStayArrivingNow() {
    var wizard = wizard();
    wizard.cotizar();
    assertThat(wizard.precio.getNoches()).isEqualTo("2 noches");
    assertThat(wizard.precio.getPrecioNoche()).isEqualTo("206,00 EUR");
    assertThat(wizard.precio.getTotalCrs()).isEqualTo("412,00 EUR");
    assertThat(wizard.problemaAlSalir("precio", null)).isNull();

    wizard.titular = new TitularWalkIn();
    assertThat(wizard.problemaAlSalir("titular", null)).isEqualTo("Falta del titular: nombre, apellidos, documento.");
    wizard.titular.setNombre("Nora");
    wizard.titular.setApellidos("Vega");
    wizard.titular.setDocumento("X1234567");
    wizard.resumir();
    assertThat(wizard.confirmar.getResumenPrecio()).isEqualTo("412,00 EUR · 2 noches");
    assertThat(wizard.confirmar.getResumenEstancia()).contains("Estándar king (STD-KING)");

    var result = (List<?>) wizard.confirmarWalkIn();

    var message = (Message) result.get(0);
    assertThat(message.text()).contains("abierta").contains("CRS777");
    var stayId = message.text().replaceAll("^Estancia (FO-[A-Z2-9]{6}).*$", "$1");
    assertThat(stays.findById(stayId)).get().extracting(s -> s.status()).isEqualTo(StayStatus.ARRIVING);
    assertThat(result.get(1)).isInstanceOf(UICommand.class);
  }

  @Test
  void aStayTheCrsWillNotSellHasNoPriceAndCannotGoOn() {
    refuseQuote.set(true);
    try {
      var wizard = wizard();
      wizard.cotizar();
      assertThat(wizard.precio.getErrorCrs()).isNotBlank();
      assertThat(wizard.precio.getTotalCrs()).isEqualTo("—");
      assertThat(wizard.problemaAlSalir("precio", null)).startsWith("Sin precio del CRS");
    } finally {
      refuseQuote.set(false);
    }
  }

  @Test
  void aStayChangedAfterThePriceWasGivenCannotBeConfirmedAtThatPrice() {
    var wizard = wizard();
    wizard.cotizar();
    wizard.estancia.setAdultos(3);

    assertThat(wizard.problemaAlSalir("precio", null)).startsWith("Sin precio del CRS");
    assertThat(((Message) wizard.confirmarWalkIn()).text()).startsWith("Calcula el precio");
  }
}
