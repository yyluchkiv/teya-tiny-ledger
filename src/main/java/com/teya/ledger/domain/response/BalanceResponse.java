package com.teya.ledger.domain.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

public record BalanceResponse(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {
}
