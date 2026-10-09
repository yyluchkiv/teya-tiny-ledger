package com.teya.ledger.service;

import com.teya.ledger.data.Ledger;
import com.teya.ledger.domain.base.Transfer;
import com.teya.ledger.domain.exceptions.AccountNotFoundException;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import com.teya.ledger.domain.exceptions.SameAccountTransferException;
import com.teya.ledger.domain.exceptions.TransferNotFoundException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

    private final Ledger ledger;

    public TransferService(Ledger ledger) {
        this.ledger = ledger;
    }

    /**
     * Moves money between two accounts atomically: either both legs are recorded or neither is.
     *
     * @throws SameAccountTransferException if both ids are the same account
     * @throws AccountNotFoundException if either account does not exist
     * @throws InsufficientFundsException if the source balance does not cover the amount
     */
    public Transfer transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String description) {
        return ledger.transfer(fromAccountId, toAccountId, Amounts.normalise(amount), description);
    }

    /** @throws TransferNotFoundException if no transfer with this id exists */
    public Transfer getTransfer(UUID id) {
        return ledger.findTransfer(id).orElseThrow(() -> new TransferNotFoundException(id));
    }
}
