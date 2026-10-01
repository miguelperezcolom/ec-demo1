package io.mateu.ecdemo1.frontoffice.ui.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlagsTest {

  @Test
  void aCountryCodeIsItsFlag() {
    assertThat(Flags.of("ES")).isEqualTo("🇪🇸");
    assertThat(Flags.of("de")).isEqualTo("🇩🇪");
    assertThat(Flags.of(" it ")).isEqualTo("🇮🇹");
  }

  @Test
  void ukIsGreatBritain() {
    assertThat(Flags.of("UK")).isEqualTo("🇬🇧");
  }

  @Test
  void whatIsNotACountryGivesNothing() {
    assertThat(Flags.of(null)).isEmpty();
    assertThat(Flags.of("")).isEmpty();
    assertThat(Flags.of("ESP")).isEmpty();
    assertThat(Flags.of("XX")).isEmpty();
    assertThat(Flags.of("1A")).isEmpty();
  }

  @Test
  void theNameGoesAfterTheFlagOrAlone() {
    assertThat(Flags.before("FR", "Anne Martin")).isEqualTo("🇫🇷 Anne Martin");
    assertThat(Flags.before(null, "Anne Martin")).isEqualTo("Anne Martin");
  }

  @Test
  void theImageIsTheBundledSvgOfTheCountry() {
    assertThat(Flags.image("AT")).isEqualTo("/flags/at.svg");
    assertThat(Flags.image("uk")).isEqualTo("/flags/gb.svg");
    assertThat(Flags.image("ESP")).isNull();
    assertThat(Flags.image(null)).isNull();
    assertThat(Flags.codeOf(Flags.image("at"))).isEqualTo("AT");
    assertThat(Flags.codeOf(null)).isEmpty();
  }

  @Test
  void everyCountryHasItsFlagBundled() {
    for (var country : Flags.COUNTRIES) {
      var path = "static" + Flags.image(country);
      assertThat(getClass().getClassLoader().getResource(path)).as(path).isNotNull();
    }
  }
}
