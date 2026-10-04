package com.teya.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

    private ResponseEntity<JsonNode> postTransaction(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/transactions", new HttpEntity<>(json, headers), JsonNode.class);
    }
}
