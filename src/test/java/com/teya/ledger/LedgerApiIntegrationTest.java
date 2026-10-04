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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class LedgerApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void depositWithdrawAndOverdraftFlow() {
        // Arrange
        String deposit = """
                {"type":"DEPOSIT","amount":"100.00","description":"salary"}
                """;
        String withdrawal = """
                {"type":"WITHDRAWAL","amount":"30.00","description":"groceries"}
                """;
        String overdraft = """
                {"type":"WITHDRAWAL","amount":"1000.00"}
                """;

        // Act
        ResponseEntity<JsonNode> depositResponse = postTransaction(deposit);
        ResponseEntity<JsonNode> withdrawalResponse = postTransaction(withdrawal);
        ResponseEntity<JsonNode> balanceAfterWithdrawal = restTemplate.getForEntity("/api/v1/balance", JsonNode.class);
        ResponseEntity<JsonNode> history = restTemplate.getForEntity("/api/v1/transactions", JsonNode.class);
        ResponseEntity<JsonNode> overdraftResponse = postTransaction(overdraft);
        ResponseEntity<JsonNode> balanceAfterOverdraft = restTemplate.getForEntity("/api/v1/balance", JsonNode.class);

        // Assert
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(depositResponse.getHeaders().getLocation()).isNotNull();
        assertThat(withdrawalResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(balanceAfterWithdrawal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balanceAfterWithdrawal.getBody().get("balance").asText()).isEqualTo("70.00");

        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).hasSize(2);
        assertThat(history.getBody().get(0).get("type").asText()).isEqualTo("WITHDRAWAL");
        assertThat(history.getBody().get(1).get("type").asText()).isEqualTo("DEPOSIT");

        assertThat(overdraftResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(balanceAfterOverdraft.getBody().get("balance").asText()).isEqualTo("70.00");
    }

    private ResponseEntity<JsonNode> postTransaction(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/transactions", new HttpEntity<>(json, headers), JsonNode.class);
    }
}
