package com.example.bankapp.controller;

import com.example.bankapp.config.SecurityConfig;
import com.example.bankapp.model.Account;
import com.example.bankapp.model.Transaction;
import com.example.bankapp.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MVC slice tests for {@link BankController}.
 * The real Spring Security filter chain (SecurityConfig) is loaded so
 * that authenticated vs. permit-all routes behave exactly as in production,
 * while AccountService is mocked so no database is required.
 */
@WebMvcTest(BankController.class)
@Import(SecurityConfig.class)
class BankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    private Account buildAccount(String username, String balance) {
        Account account = new Account();
        account.setId(1L);
        account.setUsername(username);
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    // ---------- /login & /register (permitAll) ----------

    @Test
    void login_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void showRegistrationForm_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void registerAccount_redirectsToLogin_onSuccess() throws Exception {
        when(accountService.registerAccount("newuser", "pass123"))
                .thenReturn(buildAccount("newuser", "0"));

        mockMvc.perform(post("/register")
                        .param("username", "newuser")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void registerAccount_returnsFormWithError_whenUsernameTaken() throws Exception {
        when(accountService.registerAccount("john", "pass123"))
                .thenThrow(new RuntimeException("Username already exists"));

        mockMvc.perform(post("/register")
                        .param("username", "john")
                        .param("password", "pass123"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("error", "Username already exists"));
    }

    // ---------- /dashboard (requires auth) ----------

    @Test
    void dashboard_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "john")
    void dashboard_showsAccountForAuthenticatedUser() throws Exception {
        when(accountService.findAccountByUsername("john")).thenReturn(buildAccount("john", "100.00"));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("account"));
    }

    // ---------- /deposit ----------

    @Test
    @WithMockUser(username = "john")
    void deposit_redirectsToDashboard_onSuccess() throws Exception {
        when(accountService.findAccountByUsername("john")).thenReturn(buildAccount("john", "100.00"));

        mockMvc.perform(post("/deposit").param("amount", "25.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        verify(accountService).deposit(any(Account.class), eq(new BigDecimal("25.00")));
    }

    // ---------- /withdraw ----------

    @Test
    @WithMockUser(username = "john")
    void withdraw_redirectsToDashboard_onSuccess() throws Exception {
        when(accountService.findAccountByUsername("john")).thenReturn(buildAccount("john", "100.00"));

        mockMvc.perform(post("/withdraw").param("amount", "25.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @WithMockUser(username = "john")
    void withdraw_returnsDashboardWithError_whenInsufficientFunds() throws Exception {
        Account account = buildAccount("john", "10.00");
        when(accountService.findAccountByUsername("john")).thenReturn(account);
        doThrow(new RuntimeException("Insufficient funds"))
                .when(accountService).withdraw(eq(account), eq(new BigDecimal("500.00")));

        mockMvc.perform(post("/withdraw").param("amount", "500.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("error", "Insufficient funds"));
    }

    // ---------- /transactions ----------

    @Test
    @WithMockUser(username = "john")
    void transactionHistory_returnsTransactionsView() throws Exception {
        Account account = buildAccount("john", "100.00");
        when(accountService.findAccountByUsername("john")).thenReturn(account);
        when(accountService.getTransactionHistory(account))
                .thenReturn(List.of(new Transaction(new BigDecimal("10"), "Deposit", null, account)));

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions"))
                .andExpect(model().attributeExists("transactions"));
    }

    // ---------- /transfer ----------

    @Test
    @WithMockUser(username = "john")
    void transfer_redirectsToDashboard_onSuccess() throws Exception {
        Account account = buildAccount("john", "100.00");
        when(accountService.findAccountByUsername("john")).thenReturn(account);

        mockMvc.perform(post("/transfer")
                        .param("toUsername", "jane")
                        .param("amount", "20.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        verify(accountService).transferAmount(eq(account), eq("jane"), eq(new BigDecimal("20.00")));
    }

    @Test
    @WithMockUser(username = "john")
    void transfer_returnsDashboardWithError_whenRecipientMissing() throws Exception {
        Account account = buildAccount("john", "100.00");
        when(accountService.findAccountByUsername("john")).thenReturn(account);
        doThrow(new RuntimeException("Recipient account not found"))
                .when(accountService).transferAmount(eq(account), eq("ghost"), any(BigDecimal.class));

        mockMvc.perform(post("/transfer")
                        .param("toUsername", "ghost")
                        .param("amount", "20.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("error", "Recipient account not found"));
    }
}