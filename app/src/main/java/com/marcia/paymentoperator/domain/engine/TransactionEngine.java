package com.marcia.paymentoperator.domain.engine;

import androidx.lifecycle.LiveData;

import com.elecctro.recruitment.paymentterminal.AuthorizationResult;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGateway;

import java.util.UUID;

public class TransactionEngine {

    private final PaymentTerminalGateway gateway;

    public TransactionEngine(PaymentTerminalGateway gateway) {
        this.gateway = gateway;
    }

    public LiveData<AuthorizationResult> authorize(long amount) {
        UUID id = UUID.randomUUID();
        return gateway.authorize(id, amount);
    }
}
