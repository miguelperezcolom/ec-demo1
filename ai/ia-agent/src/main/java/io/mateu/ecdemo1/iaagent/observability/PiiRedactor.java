package io.mateu.ecdemo1.iaagent.observability;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks the personal data that can be recognised by its shape — e-mail addresses, phone numbers,
 * card numbers, Spanish DNI/NIE, passport numbers and IBANs — with a marker naming what was there
 * ({@code [email]}, {@code [phone]}, {@code [card]}, {@code [iban]}, {@code [dni]}, {@code [nie]},
 * {@code [passport]}). The sentence around it stays readable, and a JSON document stays JSON: a
 * marker replaces the value inside its quotes and adds none of its own.
 *
 * <p><strong>What it cannot see.</strong> Names, addresses and anything else that is personal by
 * meaning rather than by shape pass through unchanged. That is why {@code redacted} is the mode
 * for real data and not a guarantee: it takes out what a pattern can find.
 *
 * <p>Tuned to leave alone what this demo's conversations are full of: booking and hotel codes
 * ({@code MRU01}, {@code XZ6GDG}, {@code NORDTRAVEL}), dates, amounts, UUIDs and ids. Two
 * precautions carry most of that: a number glued to a letter, a dash or a dot is never taken for a
 * phone or a card (UUIDs, dates, decimals), and a run of digits is a card only when it passes the
 * Luhn check and starts like a card does.
 */
public final class PiiRedactor {

    private PiiRedactor() {}

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");

    /**
     * Country, two check digits, then groups of four — with or without the spaces. Taken only when
     * it is mostly digits (see {@link #mostlyDigits}), so a line of capitals is not an IBAN.
     */
    private static final Pattern IBAN = Pattern.compile(
            "(?<![A-Za-z0-9])[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){3,7}(?: ?[A-Z0-9]{1,3})?(?![A-Za-z0-9])");

    /** 13 to 19 digits, single spaces or dashes between them; confirmed by Luhn below. */
    private static final Pattern CARD = Pattern.compile(
            "(?<![\\w.,/-])[2-6](?:[ -]?\\d){12,18}(?![\\w]|[.,/-]\\d)");

    /** A leading + and a country code: +34 612 345 678, +44 20 7946 0958. */
    private static final Pattern PHONE_INTL = Pattern.compile(
            "(?<![\\w+])\\+\\d{1,3}(?:[ .-]?\\d){6,12}(?![\\w]|[.,/-]\\d)");

    /** Spanish numbering: nine digits starting 6, 7, 8 or 9, optionally 0034 in front. */
    private static final Pattern PHONE_ES = Pattern.compile(
            "(?<![\\w.,/+-])(?:0034[ .-]?)?[6789](?:[ .-]?\\d){8}(?![\\w]|[.,/-]\\d)");

    private static final Pattern NIE = Pattern.compile(
            "(?<![\\w-])[XYZxyz][ -]?\\d{7}[ -]?[A-Za-z](?![\\w])");

    private static final Pattern DNI = Pattern.compile(
            "(?<![\\w-])\\d{8}[ -]?[A-Za-z](?![\\w])");

    /** The Spanish format — three letters and six digits — wherever it appears. */
    private static final Pattern PASSPORT = Pattern.compile(
            "(?<![\\w-])[A-Z]{3}\\d{6}(?![\\w])");

    /** Any format, when the text says it is a passport: "pasaporte nº AB1234567". */
    private static final Pattern PASSPORT_LABELLED = Pattern.compile(
            "(?iu)(pasaporte|passport)(\\W{0,3}(?:n(?:[º°o.]|úm(?:ero)?|um(?:ber)?)?\\W{0,3})?)([A-Z0-9]{6,9})(?![\\w])");

    /**
     * The text with every recognisable piece of personal data replaced. Null in, null out.
     * Order matters: an e-mail or an IBAN contains runs of digits the later patterns would
     * otherwise take for something else.
     */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = EMAIL.matcher(text).replaceAll("[email]");
        out = PASSPORT_LABELLED.matcher(out).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + m.group(2) + "[passport]"));
        out = replaceIf(out, IBAN, PiiRedactor::mostlyDigits, m -> "[iban]");
        out = replaceIf(out, CARD, PiiRedactor::luhn, m -> "[card]");
        out = PHONE_INTL.matcher(out).replaceAll("[phone]");
        out = PHONE_ES.matcher(out).replaceAll("[phone]");
        out = NIE.matcher(out).replaceAll("[nie]");
        out = DNI.matcher(out).replaceAll("[dni]");
        out = PASSPORT.matcher(out).replaceAll("[passport]");
        return out;
    }

    private static String replaceIf(String text, Pattern pattern, Predicate<MatchResult> when,
                                    Function<MatchResult, String> with) {
        return pattern.matcher(text).replaceAll(m -> Matcher.quoteReplacement(
                when.test(m) ? with.apply(m) : m.group()));
    }

    private static boolean mostlyDigits(MatchResult m) {
        return m.group().chars().filter(Character::isDigit).count() >= 10;
    }

    private static boolean luhn(MatchResult m) {
        return luhn(m.group());
    }

    static boolean luhn(String candidate) {
        int sum = 0;
        int digits = 0;
        boolean doubleIt = false;
        for (int i = candidate.length() - 1; i >= 0; i--) {
            char c = candidate.charAt(i);
            if (!Character.isDigit(c)) {
                continue;
            }
            int d = c - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            digits++;
            doubleIt = !doubleIt;
        }
        return digits >= 13 && sum % 10 == 0;
    }
}
