package com.marcia.paymentoperator.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.marcia.paymentoperator.domain.model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TransactionRepositoryImpl implements TransactionRepository {

    private final Map<UUID, Transaction> transactions = new HashMap<>();

    private final MutableLiveData<List<Transaction>> transactionLiveData = new MutableLiveData<>();

    public TransactionRepositoryImpl() {
        transactionLiveData.setValue(new ArrayList<>());
    }

    @Override
    public LiveData<List<Transaction>> observeTransactions() {
        return transactionLiveData;
    }

    @Override
    public void save(Transaction transaction) {
        transactions.put(transaction.getId(), transaction);
        publish();
    }

    @Override
    public Transaction getTransaction(UUID id) {
        return transactions.get(id);
    }


    @Override
    public List<Transaction> getAll() {
        return new ArrayList<>(transactions.values());
    }

    @Override
    public void update(Transaction transaction) {
        transactions.put(transaction.getId(), transaction);
        publish();
    }

    private void publish() {
        transactionLiveData.setValue(
                new ArrayList<>(transactions.values())
        );
    }

}
