package com.marcia.paymentoperator;

import com.elecctro.recruitment.paymentterminal.PaymentTerminal;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGateway;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGatewayImpl;
import com.marcia.paymentoperator.data.repository.TransactionRepository;
import com.marcia.paymentoperator.data.repository.TransactionRepositoryImpl;
import com.marcia.paymentoperator.domain.engine.TransactionEngine;

import java.io.File;

public final class PaymentOperatorFactory {

    private static final String TRANSACTION_JOURNAL_FILENAME = "transactions.properties";

    private PaymentOperatorFactory() {
    }

    public static TransactionEngine create(File filesDir) {
        PaymentTerminal terminal = PaymentTerminal.create(PaymentTerminal.Mode.REAL);
        PaymentTerminalGateway gateway = new PaymentTerminalGatewayImpl(terminal);
        TransactionRepository repository =
                new TransactionRepositoryImpl(new File(filesDir, TRANSACTION_JOURNAL_FILENAME));
        return new TransactionEngine(gateway, repository);
    }
}
