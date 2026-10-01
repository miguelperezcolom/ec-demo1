package io.mateu.ecdemo1.frontoffice.ui.common;

import java.util.Locale;
import java.util.Set;

/**
 * A nationality as its flag: an ISO 3166-1 alpha-2 code written as its two regional-indicator
 * letters (ES → 🇪🇸), which the system's emoji font draws as the flag. Anything that is not a
 * country's code — blank, three letters, a code no country has — gives nothing: a guest with no
 * known nationality simply shows no flag.
 *
 * <p>Windows does not draw flag emoji: there the two letters show instead («ES»), which still reads.
 */
public final class Flags {

  static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

  private Flags() {
  }

  /** The flag of a nationality's ISO code, or "" when it is not one. Lowercase is taken; UK is GB. */
  public static String of(String code) {
    var iso = iso(code);
    if (iso == null) {
      return "";
    }
    return new String(Character.toChars(0x1F1E6 + iso.charAt(0) - 'A'))
        + new String(Character.toChars(0x1F1E6 + iso.charAt(1) - 'A'));
  }

  /** The name with its flag in front — or the name alone when there is no flag. */
  public static String before(String code, String name) {
    var flag = of(code);
    return flag.isEmpty() || name == null ? name : flag + " " + name;
  }

  /**
   * The flag as an IMAGE: the path of its bundled SVG (static/flags, the 1x1 set of flag-icons, MIT),
   * or null when it is not a country's code. What a table cell shows — it draws the same everywhere,
   * where the emoji depends on the font (the Redwood table and Windows show the two letters).
   */
  public static String image(String code) {
    var iso = iso(code);
    return iso == null ? null : "/flags/" + iso.toLowerCase(Locale.ROOT) + ".svg";
  }

  /** The country code an {@link #image} path is the flag of ("AT" for /flags/at.svg), or "". */
  public static String codeOf(String image) {
    if (image == null || !image.startsWith("/flags/") || !image.endsWith(".svg")) {
      return "";
    }
    return image.substring("/flags/".length(), image.length() - ".svg".length()).toUpperCase(Locale.ROOT);
  }

  /** The two-letter code, normalised, if it is a country's; null otherwise. */
  static String iso(String code) {
    if (code == null) {
      return null;
    }
    var c = code.trim().toUpperCase(Locale.ROOT);
    if ("UK".equals(c)) {
      c = "GB";
    }
    return c.length() == 2 && COUNTRIES.contains(c) ? c : null;
  }
}
