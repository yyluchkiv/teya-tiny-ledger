package com.teya.ledger.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teya.ledger.domain.base.Transfer;
import com.teya.ledger.domain.exceptions.AccountNotFoundException;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import com.teya.ledger.domain.exceptions.SameAccountTransferException;
import com.teya.ledger.domain.exceptions.TransferNotFoundException;
import com.teya.ledger.service.TransferService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final UUID FROM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @Test
    void postTransferReturnsCreatedWithLocation() throws Exception {
        // Arrange
        Transfer transfer = transfer();
        when(transferService.transfer(eq(FROM), eq(TO), any(BigDecimal.class), eq("save"))).thenReturn(transfer);

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.00", "\"save\"")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/transfers/" + transfer.id()))
                .andExpect(jsonPath("$.id").value(transfer.id().toString()))
                .andExpect(jsonPath("$.fromAccountId").value(FROM.toString()))
                .andExpect(jsonPath("$.toAccountId").value(TO.toString()))
                .andExpect(jsonPath("$.amount").value("10.00"))
                .andExpect(jsonPath("$.description").value("save"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-09T12:00:00Z"))
                .andExpect(jsonPath("$.debitTransactionId").value(transfer.debitTransactionId().toString()))
                .andExpect(jsonPath("$.creditTransactionId").value(transfer.creditTransactionId().toString()));
    }

    @Test
    void missingFieldsReturnBadRequestListingEachField() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fromAccountId").exists())
                .andExpect(jsonPath("$.errors.toAccountId").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(transferService);
    }

    @Test
    void invalidAmountReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1.001", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        verifyNoInteractions(transferService);
    }

    @Test
    void malformedAccountIdReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"nope","toAccountId":"%s","amount":"1.00"}
                                """.formatted(TO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
        verifyNoInteractions(transferService);
    }

    @Test
    void sameAccountTransferReturnsBadRequest() throws Exception {
        // Arrange
        when(transferService.transfer(eq(FROM), eq(FROM), any(BigDecimal.class), any()))
                .thenThrow(new SameAccountTransferException(FROM));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":"1.00"}
                                """.formatted(FROM, FROM)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid transfer"));
    }

    @Test
    void unknownAccountReturnsNotFound() throws Exception {
        // Arrange
        when(transferService.transfer(eq(FROM), eq(TO), any(BigDecimal.class), any()))
                .thenThrow(new AccountNotFoundException(TO));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("1.00", "null")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.id").value(TO.toString()));
    }

    @Test
    void insufficientFundsReturnsUnprocessableEntity() throws Exception {
        // Arrange
        when(transferService.transfer(eq(FROM), eq(TO), any(BigDecimal.class), any()))
                .thenThrow(new InsufficientFundsException(new BigDecimal("5000.00"), new BigDecimal("1084.50")));

        // Act & Assert
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("5000.00", "null")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.requestedAmount").value("5000.00"))
                .andExpect(jsonPath("$.currentBalance").value("1084.50"));
    }

    @Test
    void getTransferReturnsTransfer() throws Exception {
        // Arrange
        Transfer transfer = transfer();
        when(transferService.getTransfer(transfer.id())).thenReturn(transfer);

        // Act & Assert
        mockMvc.perform(get("/api/v1/transfers/{id}", transfer.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transfer.id().toString()))
                .andExpect(jsonPath("$.amount").value("10.00"));
    }

    @Test
    void unknownTransferReturnsNotFound() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(transferService.getTransfer(id)).thenThrow(new TransferNotFoundException(id));

        // Act & Assert
        mockMvc.perform(get("/api/v1/transfers/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transfer not found"))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void malformedTransferIdReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/transfers/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verifyNoInteractions(transferService);
    }

    private static Transfer transfer() {
        return new Transfer(UUID.randomUUID(), FROM, TO, new BigDecimal("10.00"), "save", NOW,
                UUID.randomUUID(), UUID.randomUUID());
    }

    private static String body(String amount, String description) {
        return """
                {"fromAccountId":"%s","toAccountId":"%s","amount":"%s","description":%s}
                """.formatted(FROM, TO, amount, description);
    }
}
