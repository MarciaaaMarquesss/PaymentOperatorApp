package com.marcia.paymentoperator.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.marcia.paymentoperator.PaymentOperatorFactory;
import com.marcia.paymentoperator.domain.engine.TransactionEngine;
import com.marcia.paymentoperator.domain.model.Transaction;
import com.marcia.paymentoperator.recovery.RecoveryManager;

import java.util.List;

public class PaymentOperatorViewModel extends AndroidViewModel {

    private final TransactionEngine engine;
    private final LiveData<List<Transaction>> transactions;

    public PaymentOperatorViewModel(Application application) {
        super(application);
        engine = PaymentOperatorFactory.create(application.getFilesDir());
        transactions = engine.observeTransactions();
        new RecoveryManager(engine).recover();
    }

    public LiveData<List<Transaction>> observeTransactions() {
        return transactions;
    }

    public Transaction authorize(long amount) {
        return engine.authorize(amount);
    }

    public boolean capture(Transaction transaction, long amount) {
        if (transaction == null) {
            return false;
        }
        return engine.capture(transaction.getId(), amount);
    }

    public boolean cancel(Transaction transaction) {
        if (transaction == null) {
            return false;
        }
        return engine.cancel(transaction.getId());
    }

    public boolean retry(Transaction transaction) {
        if (transaction == null) {
            return false;
        }
        return engine.retry(transaction.getId());
    }
}
