package io.mateu.ecdemo1.iaagent.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiRedactorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // e-mail
            "Escríbeme a maria.garcia@example.com, gracias|Escríbeme a [email], gracias",
            "maria.garcia+reservas@mail.example.co.uk|[email]",
            // phones: Spanish mobile and landline, with and without prefix and separators
            "Mi móvil es 612345678.|Mi móvil es [phone].",
            "Llamad al 612 345 678 o al 91 123 45 67|Llamad al [phone] o al [phone]",
            "tel: 612-34-56-78|tel: [phone]",
            "+34 612 34 56 78|[phone]",
            "0034 912345678|[phone]",
            "UK office +44 20 7946 0958|UK office [phone]",
            "Ana (ana@example.com, +34 612 345 678) confirmada|Ana ([email], [phone]) confirmada",
            // cards, Luhn-valid only
            "Visa 4111 1111 1111 1111 caduca 12/28|Visa [card] caduca 12/28",
            "5500-0000-0000-0004|[card]",
            "Amex 378282246310005|Amex [card]",
            // IBAN
            "IBAN ES91 2100 0418 4502 0005 1332.|IBAN [iban].",
            "ES9121000418450200051332|[iban]",
            "DE89370400440532013000|[iban]",
            // DNI / NIE
            "DNI 12345678Z|DNI [dni]",
            "dni: 12345678-z|dni: [dni]",
            "NIE X1234567L|NIE [nie]",
            "nie y-1234567-x|nie [nie]",
            // passports
            "pasaporte PAA123456|pasaporte [passport]",
            "Titular con PAA123456 en vigor|Titular con [passport] en vigor",
            "pasaporte nº AB1234567|pasaporte nº [passport]",
            "Passport number: 123456789|Passport number: [passport]",
    })
    void masksEachKindOfPersonalData(String input, String expected) {
        assertEquals(expected, PiiRedactor.redact(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Reserva XZ6GDG en el hotel MRU01 para NORDTRAVEL",
            "Del 2026-10-01 al 05/10/2026, check-in 2026-09-27T10:15:30Z",
            "Total 1.234,56 € (1234.56 EUR), tasa 350 €, 12,5 %",
            "booking 550e8400-e29b-41d4-a716-446655440000, room 2, 3 adultos",
            "ref 123456, 2 noches, precio 912.34 EUR",
            "Hotels: MRU01, PMI02, BCN10; agencies NORDTRAVEL, SOLTOURS",
            "epoch 1727430000000",
            "Tarjeta 4111 1111 1111 1112 no válida",
            "EL AGENTE HA DICHO QUE TODO ESTA BIEN",
            "Hola, ¿qué reservas tiene el hotel MRU01 para mañana?",
    })
    void leavesOrdinaryTextAlone(String text) {
        assertEquals(text, PiiRedactor.redact(text));
    }

    @Test
    void keepsJsonValidAndReadable() {
        var json = "{\"bookingCode\":\"XZ6GDG\",\"hotel\":\"MRU01\",\"email\":\"ana@example.com\","
                + "\"phone\":\"+34 612 345 678\",\"amount\":1234.56}";
        assertEquals("{\"bookingCode\":\"XZ6GDG\",\"hotel\":\"MRU01\",\"email\":\"[email]\","
                + "\"phone\":\"[phone]\",\"amount\":1234.56}", PiiRedactor.redact(json));
    }

    @Test
    void luhn() {
        assertTrue(PiiRedactor.luhn("4111111111111111"));
        assertFalse(PiiRedactor.luhn("4111111111111112"));
    }

    @Test
    void nullAndEmpty() {
        assertNull(PiiRedactor.redact(null));
        assertEquals("", PiiRedactor.redact(""));
    }

    @Test
    void contentCaptureModes() {
        var text = "Soy ana@example.com";
        assertNull(new ContentCapture("none", 16384).prepare(text));
        assertNull(new ContentCapture("", 16384).prepare(text));
        assertNull(new ContentCapture("whatever", 16384).prepare(text));
        assertEquals("Soy [email]", new ContentCapture("redacted", 16384).prepare(text));
        assertEquals(text, new ContentCapture("FULL", 16384).prepare(text));
    }

    @Test
    void contentCaptureTruncatesAndSaysSo() {
        var capture = new ContentCapture(ContentCapture.Mode.FULL, 100);
        var out = capture.prepare("x".repeat(250));
        assertTrue(out.startsWith("x".repeat(100) + "…[truncated: 250 chars, 100 kept]"), out);
        assertEquals("short", capture.prepare("short"));
    }
}
