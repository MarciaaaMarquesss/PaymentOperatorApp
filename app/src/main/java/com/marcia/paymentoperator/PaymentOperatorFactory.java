package com.marcia.paymentoperator;

import com.elecctro.recruitment.paymentterminal.PaymentTerminal;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGateway;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGatewayImpl;
import com.marcia.paymentoperator.data.repository.TransactionRepository;
import com.marcia.paymentoperator.data.repository.TransactionRepositoryImpl;
import com.marcia.paymentoperator.domain.engine.TransactionEngine;

import java.io.File;

public final class PaymentOperatorFactory {

    private PaymentOperatorFactory() {
    }

    public static TransactionEngine create(File filesDir) {
        PaymentTerminal terminal = PaymentTerminal.create(PaymentTerminal.Mode.REAL);
        PaymentTerminalGateway gateway = new PaymentTerminalGatewayImpl(terminal);
        TransactionRepository repository =
                new TransactionRepositoryImpl(new File(filesDir, "transactions.properties"));
        return new TransactionEngine(gateway, repository);
    }
}
