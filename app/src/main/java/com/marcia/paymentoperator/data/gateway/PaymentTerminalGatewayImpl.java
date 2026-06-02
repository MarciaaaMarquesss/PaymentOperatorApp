package com.marcia.paymentoperator.data.gateway;

import android.util.Log;

import androidx.lifecycle.LiveData;

import java.util.UUID;
import java.util.logging.Logger;

import com.elecctro.recruitment.paymentterminal.PaymentTerminal;
import com.elecctro.recruitment.paymentterminal.AuthorizationResult;
import com.elecctro.recruitment.paymentterminal.TerminalResult;

public class PaymentTerminalGatewayImpl implements PaymentTerminalGateway {

    private final PaymentTerminal terminal;

    public PaymentTerminalGatewayImpl(PaymentTerminal terminal) {
        this.terminal = terminal;
    }

    @Override
    public LiveData<AuthorizationResult> authorize(UUID txnId, long amountCents) {
        Log.e("MARCIA", "vou chamar o aar");
        return terminal.authorize(txnId, amountCents);
    }

    @Override
    public LiveData<TerminalResult> capture(UUID txnId, long amountCents) {
        return terminal.capture(txnId, amountCents);
    }

    @Override
    public LiveData<TerminalResult> cancel(UUID txnId) {
        return terminal.cancel(txnId);
    }
}
