package com.example.bankapp.service;

import com.example.bankapp.model.Account;
import com.example.bankapp.model.Transaction;
import com.example.bankapp.repository.AccountRepository;
import com.example.bankapp.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AccountService}.
 * All collaborators (repositories, password encoder) are mocked so these
 * tests run without a database and exercise the actual business logic.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(1L);
        account.setUsername("john");
        account.setPassword("encoded-pass");
        account.setBalance(new BigDecimal("100.00"));
    }

    // ---------- findAccountByUsername ----------

    @Test
    void findAccountByUsername_returnsAccount_whenFound() {
        when(accountRepository.findByUsername("john")).thenReturn(Optional.of(account));

        Account result = accountService.findAccountByUsername("john");

        assertEquals("john", result.getUsername());
        verify(accountRepository).findByUsername("john");
    }

    @Test
    void findAccountByUsername_throws_whenNotFound() {
        when(accountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> accountService.findAccountByUsername("ghost"));
        assertEquals("Account not found", ex.getMessage());
    }

    // ---------- registerAccount ----------

    @Test
    void registerAccount_createsAccountWithZeroBalance_andEncodedPassword() {
        when(accountRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plainpass")).thenReturn("encoded-plainpass");
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account result = accountService.registerAccount("newuser", "plainpass");

        assertEquals("newuser", result.getUsername());
        assertEquals("encoded-plainpass", result.getPassword());
        assertEquals(0, result.getBalance().compareTo(BigDecimal.ZERO));
        verify(passwordEncoder).encode("plainpass");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void registerAccount_throws_whenUsernameAlreadyExists() {
        when(accountRepository.findByUsername("john")).thenReturn(Optional.of(account));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> accountService.registerAccount("john", "whatever"));
        assertEquals("Username already exists", ex.getMessage());
        verify(accountRepository, never()).save(any());
    }

    // ---------- deposit ----------

    @Test
    void deposit_increasesBalance_andRecordsTransaction() {
        accountService.deposit(account, new BigDecimal("50.00"));

        assertEquals(0, new BigDecimal("150.00").compareTo(account.getBalance()));
        verify(accountRepository).save(account);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertEquals("Deposit", saved.getType());
        assertEquals(0, new BigDecimal("50.00").compareTo(saved.getAmount()));
        assertSame(account, saved.getAccount());
    }

    // ---------- withdraw ----------

    @Test
    void withdraw_decreasesBalance_andRecordsTransaction_whenFundsSufficient() {
        accountService.withdraw(account, new BigDecimal("40.00"));

        assertEquals(0, new BigDecimal("60.00").compareTo(account.getBalance()));
        verify(accountRepository).save(account);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals("Withdrawal", captor.getValue().getType());
    }

    @Test
    void withdraw_throws_whenFundsInsufficient() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> accountService.withdraw(account, new BigDecimal("500.00")));

        assertEquals("Insufficient funds", ex.getMessage());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // ---------- getTransactionHistory ----------

    @Test
    void getTransactionHistory_delegatesToRepository() {
        Transaction t1 = new Transaction(new BigDecimal("10"), "Deposit", null, account);
        when(transactionRepository.findByAccountId(1L)).thenReturn(List.of(t1));

        List<Transaction> result = accountService.getTransactionHistory(account);

        assertEquals(1, result.size());
        verify(transactionRepository).findByAccountId(1L);
    }

    // ---------- loadUserByUsername ----------

    @Test
    void loadUserByUsername_returnsUserDetails_withUserAuthority() {
        when(accountRepository.findByUsername("john")).thenReturn(Optional.of(account));

        UserDetails details = accountService.loadUserByUsername("john");

        assertEquals("john", details.getUsername());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("USER")));
    }

    @Test
    void loadUserByUsername_throws_whenAccountMissing() {
        when(accountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> accountService.loadUserByUsername("ghost"));
    }

    // ---------- transferAmount ----------

    @Test
    void transferAmount_movesFunds_andRecordsTwoTransactions() {
        Account recipient = new Account();
        recipient.setId(2L);
        recipient.setUsername("jane");
        recipient.setBalance(new BigDecimal("20.00"));

        when(accountRepository.findByUsername("jane")).thenReturn(Optional.of(recipient));

        accountService.transferAmount(account, "jane", new BigDecimal("30.00"));

        assertEquals(0, new BigDecimal("70.00").compareTo(account.getBalance()));
        assertEquals(0, new BigDecimal("50.00").compareTo(recipient.getBalance()));

        verify(accountRepository).save(account);
        verify(accountRepository).save(recipient);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        List<Transaction> saved = captor.getAllValues();
        assertTrue(saved.stream().anyMatch(t -> t.getType().equals("Transfer Out to jane")));
        assertTrue(saved.stream().anyMatch(t -> t.getType().equals("Transfer In from john")));
    }

    @Test
    void transferAmount_throws_whenFundsInsufficient() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> accountService.transferAmount(account, "jane", new BigDecimal("999.00")));

        assertEquals("Insufficient funds", ex.getMessage());
        verify(accountRepository, never()).findByUsername("jane");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void transferAmount_throws_whenRecipientNotFound() {
        when(accountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> accountService.transferAmount(account, "ghost", new BigDecimal("10.00")));

        assertEquals("Recipient account not found", ex.getMessage());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }
}