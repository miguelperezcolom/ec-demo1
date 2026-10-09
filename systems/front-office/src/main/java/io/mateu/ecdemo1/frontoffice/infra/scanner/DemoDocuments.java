package io.mateu.ecdemo1.frontoffice.infra.scanner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The demo scanner's documents: made up, but believable — a Spanish DNI with its right letter, or a
 * passport; a birth date that fits an adult or the child's age; a nationality. Never random: everything
 * comes from the person's name, so the same person scanned again — at this stay or another — shows the
 * same document. It is a demo: two people with the same name get the same document here.
 */
public final class DemoDocuments {

  public static final String DNI = "DNI";
  public static final String PASSPORT = "PASSPORT";
  static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
  static final String PASSPORT_LETTERS = "ABCDEFGHJKLMNPRSTUVWXYZ";
  /** Where the made-up people come from when the booking does not say: mostly Spain, as the demo's guests. */
  static final List<String> NATIONALITIES = List.of("ES", "ES", "ES", "ES", "GB", "FR", "DE", "IT", "PT", "NL");

  /**
   * A document as the scanner reads it: who it names and the document itself — its type, number,
   * the country that issued it and until when it is valid.
   */
  public record Scanned(String firstName, String lastName, String documentType, String documentNumber,
                        LocalDate birthDate, String nationality, String issuingCountry, LocalDate expiry) {

    /** Issued by the country of the nationality, and valid until the date its number gives ({@link #expiry(String)}). */
    public Scanned(String firstName, String lastName, String documentType, String documentNumber, LocalDate birthDate,
                   String nationality) {
      this(firstName, lastName, documentType, documentNumber, birthDate, nationality, nationality,
          DemoDocuments.expiry(documentNumber));
    }

    /** The same person and the same birth date, on another document. */
    public Scanned withDocument(String type, String number) {
      return new Scanned(firstName, lastName, type, number, birthDate, nationality, nationality, DemoDocuments.expiry(number));
    }

    public String fullName() {
      return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
  }

  /** A stable number from the name: letters only, no accents, lower case — "García" is "garcia". */
  public static long seed(String first, String last) {
    var key = Normalizer.normalize((first == null ? "" : first) + " " + (last == null ? "" : last), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    return hash(key);
  }

  /** The first eight bytes of the text's SHA-256. */
  public static long hash(String key) {
    try {
      var hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
      var seed = 0L;
      for (int i = 0; i < 8; i++) {
        seed = (seed << 8) | (hash[i] & 0xff);
      }
      return seed;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Eight digits and the letter they call for: the number modulo 23 picks it. */
  public static String dni(long seed) {
    var number = 10_000_000 + (int) Math.floorMod(seed, 89_999_999L);
    return String.format("%08d", number) + DNI_LETTERS.charAt(number % 23);
  }

  public static boolean validDni(String dni) {
    return dni != null && dni.matches("\\d{8}[A-Z]")
        && DNI_LETTERS.charAt(Integer.parseInt(dni.substring(0, 8)) % 23) == dni.charAt(8);
  }

  /** Two letters and seven digits, as most passports read. */
  public static String passport(long seed) {
    var s = Math.floorMod(seed >>> 7, 1_000_000_000_000L);
    return "" + PASSPORT_LETTERS.charAt((int) (s % 23)) + PASSPORT_LETTERS.charAt((int) (s / 23 % 23))
        + String.format("%07d", s / 529 % 10_000_000);
  }

  /**
   * The passport the same person brings on another trip: never the one {@link #passport} gives them —
   * the demo's «new document of a customer we already know», with whom the desk has to confirm who
   * they are.
   */
  public static String newPassport(long seed) {
    var number = passport(seed ^ 0x5DEECE66DL);
    return number.equals(passport(seed)) ? passport(seed ^ 0x2545F4914F6CDD1DL) : number;
  }

  /**
   * Until when a document is valid: some years ahead, from its number — the same document always
   * says the same. None for a document with no number.
   */
  public static LocalDate expiry(String documentNumber) {
    if (documentNumber == null || documentNumber.isBlank()) {
      return null;
    }
    var seed = hash(documentNumber.trim().toUpperCase(Locale.ROOT));
    return LocalDate.of(2027 + (int) Math.floorMod(seed, 8L), 1, 1).plusDays(Math.floorMod(seed >>> 9, 365L));
  }

  public static String nationality(long seed) {
    return NATIONALITIES.get((int) Math.floorMod(seed >>> 13, (long) NATIONALITIES.size()));
  }

  /**
   * An adult's birth date is the person's own — the same whatever the stay: between 1956 and 2000. A
   * child's fits the age the booking gives on the day it arrives.
   */
  public static LocalDate birthDate(long seed, Integer age, LocalDate arrival) {
    var day = 1 + (int) Math.floorMod(seed >>> 23, 364L);
    if (age != null && age < 18) {
      return (arrival == null ? LocalDate.now() : arrival).minusYears(age).minusDays(day);
    }
    var year = 1956 + (int) Math.floorMod(seed >>> 31, 45L);
    return LocalDate.ofYearDay(year, day);
  }

  /** The document type a number looks like. */
  public static String typeOf(String number) {
    return number != null && number.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "").matches("\\d{8}[A-Z]") ? DNI : PASSPORT;
  }

  /** A whole document for someone nobody knows yet. */
  public static Scanned generate(String first, String last, String nationality, Integer age, LocalDate arrival) {
    var seed = seed(first, last);
    var country = nationality == null || nationality.isBlank() ? nationality(seed) : nationality;
    var spanish = "ES".equalsIgnoreCase(country);
    return new Scanned(first, last, spanish ? DNI : PASSPORT, spanish ? dni(seed) : passport(seed),
        birthDate(seed, age, arrival), country);
  }

  private DemoDocuments() {}
}
