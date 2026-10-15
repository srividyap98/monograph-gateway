package com.monograph.gateway.account;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Port to the Account domain. */
public interface AccountClient {

    /**
     * Accounts for many customers in one downstream call, grouped by customer id.
     * Customers without accounts (or unknown customers) are absent from the map.
     */
    Map<String, List<Account>> findByCustomerIds(Collection<String> customerIds);
}
