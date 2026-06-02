package com.marcia.paymentoperator.recovery;

import com.marcia.paymentoperator.domain.engine.TransactionEngine;

public class RecoveryManager {

    private final TransactionEngine engine;

    public RecoveryManager(TransactionEngine engine) {
        this.engine = engine;
    }

    public void recover() {
        engine.recoverInFlight();
    }
}
