package com.marcia.paymentoperator.recovery;

import com.marcia.paymentoperator.data.repository.TransactionRepository;
import com.marcia.paymentoperator.domain.engine.TransactionEngine;
import com.marcia.paymentoperator.domain.model.Transaction;

import java.util.List;

public class RecoveryManager {

    private final TransactionRepository repository;
    private final TransactionEngine engine;

    public RecoveryManager(TransactionRepository repository,
                          TransactionEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }
    public void recover() {

        List<Transaction> txs = repository.getAll();

        for (Transaction tx : txs) {

            switch (tx.getState()) {

                case AUTHORIZING:
                    engine.cancel(tx.getId());
                    break;

                case CANCELLING:
                    engine.cancel(tx.getId());
                    break;

                case CAPTURING:
                    engine.capture(tx.getId(), tx.getAmountApproved());
                    break;

                default:
                    // AUTHORIZED / CANCELLED / DECLINED → nada a fazer
                    break;
            }
        }
    }
}