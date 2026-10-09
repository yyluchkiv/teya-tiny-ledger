package com.teya.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Each test gets a fresh context, so it starts from exactly the demo data seeded on startup: 4 transactions and a
 * balance of 1084.50.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LedgerApiIntegrationTest {

    private static final String MAIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MAIN_ACCOUNT = "/api/v1/accounts/" + MAIN_ID;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void demoDataIsAvailableOnStartup() {
        // Act
        ResponseEntity<JsonNode> balance = restTemplate.getForEntity("/api/v1/balance", JsonNode.class);
        ResponseEntity<JsonNode> history = restTemplate.getForEntity("/api/v1/transactions", JsonNode.class);

        // Assert
        assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balance.getBody().get("balance").asText()).isEqualTo("1084.50");

        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).hasSize(4);
        assertThat(history.getBody().get(0).get("description").asText()).isEqualTo("freelance");
        assertThat(history.getBody().get(3).get("description").asText()).isEqualTo("salary");
    }

    @Test
    void depositWithdrawAndOverdraftFlow() {
        // Arrange
        String deposit = """
                {"type":"DEPOSIT","amount":"100.00","description":"bonus"}
                """;
        String withdrawal = """
                {"type":"WITHDRAWAL","amount":"30.00","description":"dinner"}
                """;
        String overdraft = """
                {"type":"WITHDRAWAL","amount":"5000.00"}
                """;

        // Act
        ResponseEntity<JsonNode> depositResponse = postTransaction(deposit);
        ResponseEntity<JsonNode> depositByLocation =
                restTemplate.getForEntity(depositResponse.getHeaders().getLocation(), JsonNode.class);
        ResponseEntity<JsonNode> withdrawalResponse = postTransaction(withdrawal);
        ResponseEntity<JsonNode> balanceAfterWithdrawal = restTemplate.getForEntity("/api/v1/balance", JsonNode.class);
        ResponseEntity<JsonNode> history = restTemplate.getForEntity("/api/v1/transactions", JsonNode.class);
        ResponseEntity<JsonNode> overdraftResponse = postTransaction(overdraft);
        ResponseEntity<JsonNode> balanceAfterOverdraft = restTemplate.getForEntity("/api/v1/balance", JsonNode.class);

        // Assert
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(depositByLocation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(depositByLocation.getBody()).isEqualTo(depositResponse.getBody());
        assertThat(withdrawalResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(balanceAfterWithdrawal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balanceAfterWithdrawal.getBody().get("balance").asText()).isEqualTo("1154.50");

        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).hasSize(6);
        assertThat(history.getBody().get(0).get("description").asText()).isEqualTo("dinner");
        assertThat(history.getBody().get(1).get("description").asText()).isEqualTo("bonus");

        assertThat(overdraftResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(overdraftResponse.getBody().get("currentBalance").asText()).isEqualTo("1154.50");
        assertThat(balanceAfterOverdraft.getBody().get("balance").asText()).isEqualTo("1154.50");
    }

    @Test
    void legacyEndpointsActOnTheDefaultAccount() {
        // Arrange
        String deposit = """
                {"type":"DEPOSIT","amount":"15.50"}
                """;

        // Act
        ResponseEntity<JsonNode> depositResponse = postTransaction(deposit);
        ResponseEntity<JsonNode> legacyBalance = restTemplate.getForEntity("/api/v1/balance", JsonNode.class);
        ResponseEntity<JsonNode> accountBalance =
                restTemplate.getForEntity(MAIN_ACCOUNT + "/balance", JsonNode.class);
        ResponseEntity<JsonNode> viaAccount = restTemplate.getForEntity(
                MAIN_ACCOUNT + "/transactions/" + depositResponse.getBody().get("id").asText(), JsonNode.class);

        // Assert
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(depositResponse.getBody().has("transferId")).isFalse();
        assertThat(accountBalance.getBody()).isEqualTo(legacyBalance.getBody());
        assertThat(viaAccount.getBody()).isEqualTo(depositResponse.getBody());
    }

    @Test
    void accountsAndTransferFlow() {
        // Arrange
        String createAccount = """
                {"name":"holiday"}
                """;
        BigDecimal mainBefore = balance(MAIN_ACCOUNT);

        // Act
        ResponseEntity<JsonNode> accountResponse = post("/api/v1/accounts", createAccount);
        String accountId = accountResponse.getBody().get("id").asText();
        String account = "/api/v1/accounts/" + accountId;
        ResponseEntity<JsonNode> depositResponse = post(account + "/transactions", """
                {"type":"DEPOSIT","amount":"10.00","description":"pocket money"}
                """);
        ResponseEntity<JsonNode> transferResponse = post("/api/v1/transfers", """
                {"fromAccountId":"%s","toAccountId":"%s","amount":"84.50","description":"save"}
                """.formatted(MAIN_ID, accountId));
        String transferId = transferResponse.getBody().get("id").asText();
        ResponseEntity<JsonNode> transferByLocation =
                restTemplate.getForEntity(transferResponse.getHeaders().getLocation(), JsonNode.class);
        BigDecimal mainAfterTransfer = balance(MAIN_ACCOUNT);
        BigDecimal accountAfterTransfer = balance(account);
        JsonNode mainHistory = restTemplate.getForEntity(MAIN_ACCOUNT + "/transactions", JsonNode.class).getBody();
        JsonNode accountHistory = restTemplate.getForEntity(account + "/transactions", JsonNode.class).getBody();
        ResponseEntity<JsonNode> tooMuch = post("/api/v1/transfers", """
                {"fromAccountId":"%s","toAccountId":"%s","amount":"1000.00"}
                """.formatted(accountId, MAIN_ID));
        ResponseEntity<JsonNode> sameAccount = post("/api/v1/transfers", """
                {"fromAccountId":"%s","toAccountId":"%s","amount":"1.00"}
                """.formatted(accountId, accountId));
        ResponseEntity<JsonNode> unknownAccount = post("/api/v1/transfers", """
                {"fromAccountId":"%s","toAccountId":"%s","amount":"1.00"}
                """.formatted(accountId, "22222222-2222-2222-2222-222222222222"));
        ResponseEntity<JsonNode> accounts = restTemplate.getForEntity("/api/v1/accounts", JsonNode.class);

        // Assert
        assertThat(accountResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(accountResponse.getHeaders().getLocation()).hasToString(account);
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(transferByLocation.getBody()).isEqualTo(transferResponse.getBody());
        assertThat(mainAfterTransfer).isEqualByComparingTo(mainBefore.subtract(new BigDecimal("84.50")));
        assertThat(accountAfterTransfer).isEqualByComparingTo("94.50");

        assertThat(mainHistory.get(0).get("type").asText()).isEqualTo("WITHDRAWAL");
        assertThat(mainHistory.get(0).get("transferId").asText()).isEqualTo(transferId);
        assertThat(mainHistory.get(0).get("id").asText())
                .isEqualTo(transferResponse.getBody().get("debitTransactionId").asText());
        assertThat(accountHistory).hasSize(2);
        assertThat(accountHistory.get(0).get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(accountHistory.get(0).get("transferId").asText()).isEqualTo(transferId);
        assertThat(accountHistory.get(0).get("id").asText())
                .isEqualTo(transferResponse.getBody().get("creditTransactionId").asText());
        assertThat(accountHistory.get(1).has("transferId")).isFalse();

        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(sameAccount.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknownAccount.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(balance(MAIN_ACCOUNT)).isEqualByComparingTo(mainAfterTransfer);
        assertThat(balance(account)).isEqualByComparingTo("94.50");

        assertThat(accounts.getBody()).hasSize(3);
        assertThat(accounts.getBody().get(0).get("name").asText()).isEqualTo("holiday");
        assertThat(accounts.getBody().get(1).get("name").asText()).isEqualTo("savings");
        assertThat(accounts.getBody().get(1).get("balance").asText()).isEqualTo("500.00");
        assertThat(accounts.getBody().get(2).get("id").asText()).isEqualTo(MAIN_ID);
    }

    private ResponseEntity<JsonNode> postTransaction(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/transactions", new HttpEntity<>(json, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(path, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private BigDecimal balance(String accountPath) {
        return new BigDecimal(
                restTemplate.getForEntity(accountPath + "/balance", JsonNode.class).getBody().get("balance").asText());
    }
}
