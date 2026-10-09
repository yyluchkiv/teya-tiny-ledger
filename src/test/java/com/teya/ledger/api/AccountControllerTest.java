package com.teya.ledger.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teya.ledger.domain.base.Account;
import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.domain.exceptions.AccountNotFoundException;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import com.teya.ledger.domain.exceptions.TransactionNotFoundException;
import com.teya.ledger.service.AccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void postAccountReturnsCreatedWithLocation() throws Exception {
        // Arrange
        Account account = new Account(UUID.randomUUID(), "savings", NOW);
        when(accountService.createAccount("savings")).thenReturn(account);
        when(accountService.getBalance(account.id())).thenReturn(new BigDecimal("0.00"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"savings"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/accounts/" + account.id()))
                .andExpect(jsonPath("$.id").value(account.id().toString()))
                .andExpect(jsonPath("$.name").value("savings"))
                .andExpect(jsonPath("$.balance").value("0.00"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-09T12:00:00Z"));
    }

    @Test
    void blankNameReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(accountService);
    }

    @Test
    void tooLongNameReturnsBadRequest() throws Exception {
        // Arrange
        String json = "{\"name\":\"" + "x".repeat(101) + "\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(accountService);
    }

    @Test
    void getAccountsReturnsAccountsWithBalances() throws Exception {
        // Arrange
        Account savings = new Account(UUID.randomUUID(), "savings", NOW);
        Account main = new Account(Account.DEFAULT_ID, "main", NOW.minusSeconds(60));
        when(accountService.listAccounts()).thenReturn(List.of(savings, main));
        when(accountService.getBalance(savings.id())).thenReturn(new BigDecimal("500.00"));
        when(accountService.getBalance(main.id())).thenReturn(new BigDecimal("1084.50"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("savings"))
                .andExpect(jsonPath("$[0].balance").value("500.00"))
                .andExpect(jsonPath("$[1].id").value(Account.DEFAULT_ID.toString()))
                .andExpect(jsonPath("$[1].balance").value("1084.50"));
    }

    @Test
    void getAccountReturnsAccount() throws Exception {
        // Arrange
        Account account = new Account(UUID.randomUUID(), "savings", NOW);
        when(accountService.getAccount(account.id())).thenReturn(account);
        when(accountService.getBalance(account.id())).thenReturn(new BigDecimal("12.34"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/{id}", account.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("savings"))
                .andExpect(jsonPath("$.balance").value("12.34"));
    }

    @Test
    void unknownAccountReturnsNotFound() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(accountService.getAccount(id)).thenThrow(new AccountNotFoundException(id));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void malformedAccountIdReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/not-a-uuid/balance"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verifyNoInteractions(accountService);
    }

    @Test
    void postTransactionReturnsCreatedWithAccountScopedLocation() throws Exception {
        // Arrange
        UUID accountId = UUID.randomUUID();
        Transaction transaction = new Transaction(
                UUID.randomUUID(), accountId, TransactionType.DEPOSIT, new BigDecimal("100.00"), "salary", NOW, null);
        when(accountService.recordTransaction(
                        eq(accountId), eq(TransactionType.DEPOSIT), any(BigDecimal.class), eq("salary")))
                .thenReturn(transaction);

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/{id}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":"100.00","description":"salary"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location", "/api/v1/accounts/" + accountId + "/transactions/" + transaction.id()))
                .andExpect(jsonPath("$.id").value(transaction.id().toString()))
                .andExpect(jsonPath("$.amount").value("100.00"))
                .andExpect(jsonPath("$.transferId").doesNotExist());
    }

    @Test
    void postTransactionToUnknownAccountReturnsNotFound() throws Exception {
        // Arrange
        UUID accountId = UUID.randomUUID();
        when(accountService.recordTransaction(eq(accountId), any(), any(), any()))
                .thenThrow(new AccountNotFoundException(accountId));

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/{id}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":"1.00"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"));
    }

    @Test
    void postTransactionWithInvalidAmountReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/{id}/transactions", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":"-5"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(accountService);
    }

    @Test
    void overdraftReturnsUnprocessableEntity() throws Exception {
        // Arrange
        UUID accountId = UUID.randomUUID();
        when(accountService.recordTransaction(
                        eq(accountId), eq(TransactionType.WITHDRAWAL), any(BigDecimal.class), isNull()))
                .thenThrow(new InsufficientFundsException(new BigDecimal("1000.00"), new BigDecimal("70.00")));

        // Act & Assert
        mockMvc.perform(post("/api/v1/accounts/{id}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"WITHDRAWAL","amount":"1000.00"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.currentBalance").value("70.00"));
    }

    @Test
    void getTransactionsReturnsHistoryIncludingTransferId() throws Exception {
        // Arrange
        UUID accountId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        Transaction leg = new Transaction(
                UUID.randomUUID(), accountId, TransactionType.DEPOSIT, new BigDecimal("40.00"), null, NOW, transferId);
        Transaction plain = new Transaction(
                UUID.randomUUID(), accountId, TransactionType.DEPOSIT, new BigDecimal("5.00"), null, NOW, null);
        when(accountService.getHistory(accountId)).thenReturn(List.of(leg, plain));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/{id}/transactions", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transferId").value(transferId.toString()))
                .andExpect(jsonPath("$[1].transferId").doesNotExist());
    }

    @Test
    void getTransactionByIdReturnsTransaction() throws Exception {
        // Arrange
        UUID accountId = UUID.randomUUID();
        Transaction transaction = new Transaction(
                UUID.randomUUID(), accountId, TransactionType.DEPOSIT, new BigDecimal("5.00"), null, NOW, null);
        when(accountService.getTransaction(accountId, transaction.id())).thenReturn(transaction);

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/{a}/transactions/{t}", accountId, transaction.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transaction.id().toString()));
    }

    @Test
    void unknownTransactionReturnsNotFound() throws Exception {
        // Arrange
        UUID accountId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(accountService.getTransaction(accountId, id)).thenThrow(new TransactionNotFoundException(id));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/{a}/transactions/{t}", accountId, id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transaction not found"));
    }

    @Test
    void getBalanceReturnsBalanceAsString() throws Exception {
        // Arrange
        UUID accountId = UUID.randomUUID();
        when(accountService.getBalance(accountId)).thenReturn(new BigDecimal("70.00"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("70.00"));
    }

    @Test
    void balanceOfUnknownAccountReturnsNotFound() throws Exception {
        // Arrange
        UUID accountId = UUID.randomUUID();
        when(accountService.getBalance(accountId)).thenThrow(new AccountNotFoundException(accountId));

        // Act & Assert
        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"));
    }
}
