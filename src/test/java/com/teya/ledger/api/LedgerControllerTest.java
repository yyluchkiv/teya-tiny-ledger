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

import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.exceptions.TransactionNotFoundException;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.service.LedgerService;
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

@WebMvcTest(LedgerController.class)
class LedgerControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    @Test
    void postTransactionReturnsCreatedWithLocation() throws Exception {
        // Arrange
        Transaction transaction = new Transaction(
                UUID.randomUUID(), TransactionType.DEPOSIT, new BigDecimal("100.00"), "salary", NOW);
        when(ledgerService.recordTransaction(
                        eq(TransactionType.DEPOSIT), any(BigDecimal.class), eq("salary")))
                .thenReturn(transaction);

        // Act & Assert
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":"100.00","description":"salary"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/transactions/" + transaction.id()))
                .andExpect(jsonPath("$.id").value(transaction.id().toString()))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value("100.00"))
                .andExpect(jsonPath("$.description").value("salary"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-04T12:00:00Z"));
    }

    @Test
    void getTransactionsReturnsHistory() throws Exception {
        // Arrange
        Transaction newer = new Transaction(
                UUID.randomUUID(), TransactionType.WITHDRAWAL, new BigDecimal("30.00"), null, NOW);
        Transaction older = new Transaction(
                UUID.randomUUID(), TransactionType.DEPOSIT, new BigDecimal("100.00"), "salary", NOW.minusSeconds(60));
        when(ledgerService.getHistory()).thenReturn(List.of(newer, older));

        // Act & Assert
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.id().toString()))
                .andExpect(jsonPath("$[0].amount").value("30.00"))
                .andExpect(jsonPath("$[1].id").value(older.id().toString()));
    }

    @Test
    void getTransactionByIdReturnsTransaction() throws Exception {
        // Arrange
        Transaction transaction = new Transaction(
                UUID.randomUUID(), TransactionType.DEPOSIT, new BigDecimal("100.00"), "salary", NOW);
        when(ledgerService.getTransaction(transaction.id())).thenReturn(transaction);

        // Act & Assert
        mockMvc.perform(get("/api/v1/transactions/{id}", transaction.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transaction.id().toString()))
                .andExpect(jsonPath("$.amount").value("100.00"));
    }

    @Test
    void unknownTransactionIdReturnsNotFound() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(ledgerService.getTransaction(id)).thenThrow(new TransactionNotFoundException(id));

        // Act & Assert
        mockMvc.perform(get("/api/v1/transactions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transaction not found"))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void malformedTransactionIdReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/transactions/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verifyNoInteractions(ledgerService);
    }

    @Test
    void getBalanceReturnsBalanceAsString() throws Exception {
        // Arrange
        when(ledgerService.getBalance()).thenReturn(new BigDecimal("70.00"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("70.00"));
    }

    @Test
    void negativeAmountReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":"-5"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(ledgerService);
    }

    @Test
    void missingFieldsReturnBadRequestListingEachField() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void tooManyDecimalPlacesReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":"1.001"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void unknownTypeReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TRANSFER","amount":"5.00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void insufficientFundsReturnsUnprocessableEntity() throws Exception {
        // Arrange
        when(ledgerService.recordTransaction(eq(TransactionType.WITHDRAWAL), any(BigDecimal.class), isNull()))
                .thenThrow(new InsufficientFundsException(new BigDecimal("1000.00"), new BigDecimal("70.00")));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"WITHDRAWAL","amount":"1000.00"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.requestedAmount").value("1000.00"))
                .andExpect(jsonPath("$.currentBalance").value("70.00"));
    }
}
