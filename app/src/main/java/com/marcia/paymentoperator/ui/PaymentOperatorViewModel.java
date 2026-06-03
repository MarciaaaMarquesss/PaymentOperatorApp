package com.marcia.paymentoperator.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.marcia.paymentoperator.PaymentOperatorFactory;
import com.marcia.paymentoperator.domain.engine.TransactionEngine;
import com.marcia.paymentoperator.domain.model.Transaction;
import com.marcia.paymentoperator.recovery.RecoveryManager;

import java.util.List;
import java.util.UUID;

public class PaymentOperatorViewModel extends AndroidViewModel {

    private final TransactionEngine engine;
    private final LiveData<List<Transaction>> transactions;
    private final MutableLiveData<UUID> latestAuthorizationId = new MutableLiveData<>();

    public PaymentOperatorViewModel(Application application) {
        super(application);
        engine = PaymentOperatorFactory.create(application.getFilesDir());
        transactions = engine.observeTransactions();
        new RecoveryManager(engine).recover();
    }

    public LiveData<List<Transaction>> observeTransactions() {
        return transactions;
    }

    public LiveData<UUID> observeLatestAuthorizationId() {
        return latestAuthorizationId;
    }

    public String terminalModeName() {
        return PaymentOperatorFactory.terminalModeName();
    }

    public Transaction authorize(long amount) {
        Transaction transaction = engine.authorize(amount);
        latestAuthorizationId.setValue(transaction.getId());
        return transaction;
    }

    public boolean capture(UUID transactionId, long amount) {
        if (transactionId == null) {
            return false;
        }
        return engine.capture(transactionId, amount);
    }

    public boolean cancel(UUID transactionId) {
        if (transactionId == null) {
            return false;
        }
        return engine.cancel(transactionId);
    }

    public boolean retry(UUID transactionId) {
        if (transactionId == null) {
            return false;
        }
        return engine.retry(transactionId);
    }
}
