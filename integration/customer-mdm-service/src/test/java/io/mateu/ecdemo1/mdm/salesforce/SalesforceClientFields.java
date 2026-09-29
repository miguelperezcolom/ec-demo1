package io.mateu.ecdemo1.mdm.salesforce;

import io.mateu.ecdemo1.mdm.store.Customer;

import java.util.Map;

/** The client's field maps, for the tests of other packages. */
public final class SalesforceClientFields {

    private SalesforceClientFields() {
    }

    public static Map<String, Object> contact(Customer c) {
        return SalesforceClient.contactFields(c);
    }

    public static Map<String, Object> anonymous(Customer c) {
        return SalesforceClient.anonymousFields(c);
    }
}
