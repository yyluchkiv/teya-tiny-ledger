package com.teya.ledger.api;

import com.teya.ledger.api.dto.CreateTransferRequest;
import com.teya.ledger.api.dto.TransferResponse;
import com.teya.ledger.domain.base.Transfer;
import com.teya.ledger.service.TransferService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(@Valid @RequestBody CreateTransferRequest request) {
        Transfer transfer = transferService.transfer(
                request.fromAccountId(), request.toAccountId(), request.amount(), request.description());
        URI location = URI.create("/api/v1/transfers/" + transfer.id());
        return ResponseEntity.created(location).body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse getTransfer(@PathVariable UUID id) {
        return TransferResponse.from(transferService.getTransfer(id));
    }
}
