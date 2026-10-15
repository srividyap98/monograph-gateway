package com.monograph.gateway.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;

import com.monograph.gateway.account.Account;
import com.monograph.gateway.account.AccountClient;
import com.monograph.gateway.account.AccountStatus;
import com.monograph.gateway.account.AccountType;
import com.monograph.gateway.customer.Customer;
import com.monograph.gateway.customer.CustomerClient;
import com.monograph.gateway.customer.CustomerController;
import com.monograph.gateway.customer.CustomerStatus;
import com.monograph.gateway.financial.FinancialClient;
import com.monograph.gateway.financial.FinancialController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Resolver slice test with mocked ports: proves the gateway only calls the services whose
 * fields the client actually selected. No HTTP anywhere. CustomerController is included
 * because it registers the customer DataLoader that {@code customerProfile} uses.
 */
@GraphQlTest({ CustomerProfileController.class, CustomerController.class, FinancialController.class })
class CustomerProfileControllerTest {

    private static final Customer ADA =
            new Customer("C-1001", "Ada", "Lovelace", "ada@example.com", CustomerStatus.ACTIVE);

    private static final Account CHECKING =
            new Account("A-2001", "C-1001", AccountType.CHECKING, AccountStatus.OPEN, "USD");

    @Autowired
    GraphQlTester graphQlTester;

    @MockitoBean
    CustomerClient customerClient;

    @MockitoBean
    AccountClient accountClient;

    @MockitoBean
    FinancialClient financialClient;

    @BeforeEach
    void stubCustomer() {
        given(customerClient.findByIds(any())).willReturn(Map.of("C-1001", ADA));
    }

    @Test
    void customerOnlySelectionCallsOnlyTheCustomerService() {
        graphQlTester.document("{ customerProfile(customerId: \"C-1001\") { customer { firstName } } }")
                .execute()
                .errors().verify()
                .path("customerProfile.customer.firstName").entity(String.class).isEqualTo("Ada");

        verifyNoInteractions(accountClient, financialClient);
    }

    @Test
    void accountsWithoutMoneyFieldsSkipTheFinancialService() {
        given(accountClient.findByCustomerIds(any())).willReturn(Map.of("C-1001", List.of(CHECKING)));

        graphQlTester.document("{ customerProfile(customerId: \"C-1001\") { accounts { id type } } }")
                .execute()
                .errors().verify()
                .path("customerProfile.accounts[0].type").entity(String.class).isEqualTo("CHECKING");

        verify(financialClient, never()).findBalances(anyCollection());
        verify(financialClient, never()).findTransactions(anyCollection(), anyInt());
    }

    @Test
    void unknownCustomerShortCircuitsNestedResolvers() {
        graphQlTester.document("{ customerProfile(customerId: \"C-404\") { accounts { balance { available } } } }")
                .execute()
                .errors().verify()
                .path("customerProfile").valueIsNull();

        verifyNoInteractions(accountClient, financialClient);
    }

    @Test
    void customerWithoutAccountsGetsAnEmptyList() {
        given(accountClient.findByCustomerIds(any())).willReturn(Map.of());

        graphQlTester.document("{ customerProfile(customerId: \"C-1001\") { accounts { id } } }")
                .execute()
                .errors().verify()
                .path("customerProfile.accounts").entityList(Object.class).hasSize(0);
    }

    @Test
    void missingBalanceIsNullNotAnError() {
        given(accountClient.findByCustomerIds(any())).willReturn(Map.of("C-1001", List.of(CHECKING)));
        given(financialClient.findBalances(any())).willReturn(Map.of());

        graphQlTester.document("{ customerProfile(customerId: \"C-1001\") { accounts { balance { available } } } }")
                .execute()
                .errors().verify()
                .path("customerProfile.accounts[0].balance").valueIsNull();
    }

    @Test
    void defaultTransactionLimitIsTen() {
        given(accountClient.findByCustomerIds(any())).willReturn(Map.of("C-1001", List.of(CHECKING)));
        given(financialClient.findTransactions(anyCollection(), anyInt())).willReturn(Map.of());

        graphQlTester.document("{ customerProfile(customerId: \"C-1001\") { accounts { transactions { id } } } }")
                .execute()
                .errors().verify()
                .path("customerProfile.accounts[0].transactions").entityList(Object.class).hasSize(0);

        verify(financialClient).findTransactions(anyCollection(), eq(10));
    }
}
