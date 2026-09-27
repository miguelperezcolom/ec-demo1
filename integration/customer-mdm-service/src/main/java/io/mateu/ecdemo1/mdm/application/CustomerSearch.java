package io.mateu.ecdemo1.mdm.application;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;

import java.util.Set;

/**
 * What a search of the customers asks for. Every field is optional; together they narrow it.
 *
 * @param text     every word of it is looked for in the code, the names, the email, the phone and the
 *                 document; a word of three digits or more is also looked for in the phone's digits
 * @param name     every word of it in the full name
 * @param email    part of the email
 * @param phone    part of the phone, compared by its digits when it has any
 * @param document part of the document, compared without spaces, dashes or case
 * @param statuses any of these; empty or null is any
 */
public record CustomerSearch(String text, String name, String email, String phone, String document,
                             Set<CustomerStatus> statuses) {

    public static CustomerSearch text(String text) {
        return new CustomerSearch(text, null, null, null, null, null);
    }
}
