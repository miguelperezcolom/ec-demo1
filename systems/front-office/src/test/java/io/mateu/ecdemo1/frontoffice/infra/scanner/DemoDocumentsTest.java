package io.mateu.ecdemo1.frontoffice.infra.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Period;
import org.junit.jupiter.api.Test;

/** The demo scanner's documents: believable, and always the same for the same person. */
class DemoDocumentsTest {

  @Test
  void theSamePersonAlwaysShowsTheSameDocumentHoweverTheNameIsWritten() {
    var once = DemoDocuments.generate("Lucía", "García Pérez", "ES", null, LocalDate.of(2026, 9, 27));
    var again = DemoDocuments.generate("LUCIA", "garcia perez", "ES", null, LocalDate.of(2027, 3, 1));

    assertThat(again).isEqualTo(new DemoDocuments.Scanned("LUCIA", "garcia perez", once.documentType(),
        once.documentNumber(), once.birthDate(), once.nationality()));
    assertThat(DemoDocuments.generate("Javier", "García Pérez", "ES", null, null).documentNumber())
        .isNotEqualTo(once.documentNumber());
  }

  @Test
  void aSpaniardShowsADniWithItsRightLetterAndAnyoneElseAPassport() {
    for (var name : new String[] {"Carmen", "Pablo", "Marta", "Álvaro", "Lucía", "Javier"}) {
      var dni = DemoDocuments.generate(name, "Ruiz", "ES", null, null);
      assertThat(dni.documentType()).isEqualTo("DNI");
      assertThat(DemoDocuments.validDni(dni.documentNumber())).as(dni.documentNumber()).isTrue();
    }
    assertThat(DemoDocuments.validDni("12345678Z")).isTrue();
    assertThat(DemoDocuments.validDni("12345678A")).isFalse();

    var passport = DemoDocuments.generate("Olivia", "Smith", "GB", null, null);
    assertThat(passport.documentType()).isEqualTo("PASSPORT");
    assertThat(passport.documentNumber()).matches("[A-Z]{2}\\d{7}");
    assertThat(passport.nationality()).isEqualTo("GB");
    assertThat(DemoDocuments.typeOf("12.345.678-Z")).isEqualTo("DNI");
    assertThat(DemoDocuments.typeOf("XA1234567")).isEqualTo("PASSPORT");
  }

  @Test
  void theBirthDateFitsAnAdultOrTheChildsAge() {
    var arrival = LocalDate.of(2026, 10, 5);
    var child = DemoDocuments.generate("Leo", "García", "ES", 7, arrival);
    assertThat(Period.between(child.birthDate(), arrival).getYears()).isEqualTo(7);

    var adult = DemoDocuments.generate("Ana", "García", "ES", null, arrival);
    assertThat(adult.birthDate().getYear()).isBetween(1956, 2000);
    // An adult's is their own: the same on any stay.
    assertThat(DemoDocuments.generate("Ana", "García", "ES", 40, LocalDate.of(2030, 1, 1)).birthDate())
        .isEqualTo(adult.birthDate());
  }

  @Test
  void withoutANationalityFromTheBookingOneIsChosenForThePersonAndKept() {
    var one = DemoDocuments.generate("Iker", "Sanz", null, null, null);
    assertThat(one.nationality()).isNotBlank();
    assertThat(DemoDocuments.generate("Iker", "Sanz", "", null, null).nationality()).isEqualTo(one.nationality());
    assertThat(one.documentType()).isEqualTo("ES".equals(one.nationality()) ? "DNI" : "PASSPORT");
  }
}
