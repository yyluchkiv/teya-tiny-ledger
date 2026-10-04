package com.teya.ledger.domain.exceptions;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal requestedAmount;
    private final BigDecimal currentBalance;

    public InsufficientFundsException(BigDecimal requestedAmount, BigDecimal currentBalance) {
        super("Insufficient funds: requested " + requestedAmount.toPlainString()
                + " but current balance is " + currentBalance.toPlainString());
        this.requestedAmount = requestedAmount;
        this.currentBalance = currentBalance;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }
}
