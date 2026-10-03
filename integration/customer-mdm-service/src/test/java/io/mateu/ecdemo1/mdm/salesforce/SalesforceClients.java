package io.mateu.ecdemo1.mdm.salesforce;

import io.mateu.ecdemo1.integration.model.usage.ApiCalls;
import io.mateu.ecdemo1.mdm.config.MdmProperties;
import io.mateu.ecdemo1.mdm.config.TolerantReader;
import org.springframework.web.client.RestClient;

/** A client on a given RestClient builder (a MockRestServiceServer's), for the tests of other packages. */
public final class SalesforceClients {

    private SalesforceClients() {
    }

    public static SalesforceClient of(MdmProperties properties, TolerantReader reader, SalesforceBudget budget,
                                      ApiCalls calls, RestClient.Builder builder) {
        return new SalesforceClient(properties, reader, budget, calls, builder);
    }
}
